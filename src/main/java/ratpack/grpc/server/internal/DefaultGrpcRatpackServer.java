package ratpack.grpc.server.internal;

import com.google.common.collect.Lists;
import io.grpc.BindableService;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.api.Nullable;
import ratpack.exec.internal.DefaultExecController;
import ratpack.exec.internal.ExecThreadBinding;
import ratpack.func.Action;
import ratpack.func.Function;
import ratpack.grpc.GrpcConfig;
import ratpack.grpc.server.GrpcRatpackServer;
import ratpack.grpc.server.GrpcServer;
import ratpack.impose.Impositions;
import ratpack.impose.UserRegistryImposition;
import ratpack.registry.Registry;
import ratpack.server.RatpackServer;
import ratpack.server.RatpackServerSpec;
import ratpack.server.ServerConfig;
import ratpack.server.internal.HostUtil;
import ratpack.server.internal.RatpackServerDefinition;
import ratpack.server.internal.ServerCapturer;
import ratpack.server.internal.ServerRegistry;
import ratpack.server.internal.Slf4jNoBindingDetector;
import ratpack.service.internal.DefaultEvent;
import ratpack.service.internal.ServicesGraph;
import ratpack.util.Exceptions;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultGrpcRatpackServer implements GrpcRatpackServer {

    static {
        if (System.getProperty("io.netty.leakDetectionLevel", null) == null) {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        }
    }

    public static final Logger logger = LoggerFactory.getLogger(RatpackServer.class);

    protected final Action<? super RatpackServerSpec> definitionFactory;
    protected InetSocketAddress boundAddress;
    protected DefaultExecController execController;
    protected Registry serverRegistry = Registry.empty();
    protected ServicesGraph servicesGraph;

    protected boolean reloading;
    protected final AtomicBoolean needsReload = new AtomicBoolean();

    protected boolean useSsl;
    private final Impositions impositions;

    @Nullable
    private Thread shutdownHookThread;

    private GrpcServer server;

    public DefaultGrpcRatpackServer(Action<? super RatpackServerSpec> definitionFactory, Impositions impositions) throws Exception {
        this.definitionFactory = definitionFactory;
        this.impositions = impositions;

        ServerCapturer.capture(this);
    }

    @Override
    public String getScheme() {
        return isRunning() ? this.useSsl ? "https" : "http" : null;
    }

    @Override
    public int getBindPort() {
        return server.port();
    }

    public synchronized String getBindHost() {
        if (boundAddress == null) {
            return null;
        } else {
            return HostUtil.determineHost(boundAddress);
        }
    }

    @Override
    public boolean isRunning() {
        if (server != null) {
            return server.isRunning();
        } else {
            return false;
        }
    }

    @Override
    public synchronized void start() throws Exception {
        if (isRunning()) {
            return;
        }

        try {
            ServerConfig serverConfig;

            logger.info("Starting server...");

            DefaultGrpcRatpackServer.DefinitionBuild definitionBuild = buildUserDefinition();
            if (definitionBuild.error != null) {
                if (definitionBuild.getServerConfig().isDevelopment()) {
                    logger.warn("Exception raised getting server config (will use default config until reload):", definitionBuild.error);
                    needsReload.set(true);
                } else {
                    throw Exceptions.toException(definitionBuild.error);
                }
            }

            serverConfig = definitionBuild.getServerConfig();
            execController = new DefaultExecController(serverConfig.getThreads());
            serverRegistry = ServerRegistry.serverRegistry(this, impositions, execController, serverConfig, definitionBuild.getUserRegistryFactory());
            ExecThreadBinding.bind(true, execController);

            // initialize ssl
            SslContext sslContext = serverConfig.getNettySslContext();
            this.useSsl = sslContext != null;

            // start services
            servicesGraph = new ServicesGraph(serverRegistry);
            servicesGraph.start(new DefaultEvent(serverRegistry, reloading));

            // start server
            List<BindableService> services = Lists.newArrayList(serverRegistry.getAll(BindableService.class));
            GrpcConfig grpcConfig = new GrpcConfig().port(serverConfig.getPort()).useRatpackEventLoop(true);
            server = new GrpcServer(execController, services, serverConfig, grpcConfig).start();
            boundAddress = server.address();

            String startMessage = String.format("Ratpack started %sfor %s://%s:%s", serverConfig.isDevelopment() ? "(development) " : "", getScheme(), getBindHost(), getBindPort());

            if (Slf4jNoBindingDetector.isHasBinding()) {
                if (logger.isInfoEnabled()) {
                    logger.info(startMessage);
                }
            } else {
                System.out.println(startMessage);
            }

            if (serverConfig.isRegisterShutdownHook()) {
                shutdownHookThread = new Thread("grpc-shutdown-thread") {
                    @Override
                    public void run() {
                        try {
                            DefaultGrpcRatpackServer.this.stop();
                        } catch (Exception ignored) {
                            ignored.printStackTrace(System.err);
                        }
                    }
                };
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }
        } catch (Exception e) {
            if (execController != null) {
                execController.close();
            }
            stop();
            throw e;
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (!isRunning()) {
            return;
        }

        try {
            if (shutdownHookThread != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            }
        } catch (Exception ignored) {
            // just ignore
        }

        logger.info("Stopping server...");

        try {
            if (execController != null) {
                try {
                    if (servicesGraph != null) {
                        servicesGraph.stop(new DefaultEvent(serverRegistry, reloading));
                    }
                } finally {
                    server.stop();
                    execController.close();
                }
            }
        } finally {
            this.execController = null;
        }

        logger.info("Server stopped.");
    }

    @Override
    public synchronized RatpackServer reload() throws Exception {
        reloading = true;
        boolean start = false;

        if (this.isRunning()) {
            start = true;
            this.stop();
        }

        if (start) {
            start();
        }

        reloading = false;
        return this;
    }

    @Override
    public Optional<Registry> getRegistry() {
        return Optional.of(this.serverRegistry);
    }

    private InetSocketAddress buildSocketAddress(ServerConfig serverConfig) {
        return (serverConfig.getAddress() == null) ? new InetSocketAddress(serverConfig.getPort()) : new InetSocketAddress(serverConfig.getAddress(), serverConfig.getPort());
    }

    private static class DefinitionBuild {
        private final Impositions impositions;
        private final RatpackServerDefinition definition;
        private final Throwable error;
        private final ServerConfig serverConfig;
        private final Function<? super Registry, ? extends Registry> userRegistryFactory;

        public DefinitionBuild(Impositions impositions, RatpackServerDefinition definition, Throwable error) {
            this.impositions = impositions;
            this.definition = definition;
            this.error = error;
            this.serverConfig = definition.getServerConfig();

            this.userRegistryFactory = baseRegistry -> {
                Registry userRegistry = definition.getRegistry().apply(baseRegistry);
                Registry userRegistryOverrides = impositions.get(UserRegistryImposition.class)
                        .orElse(UserRegistryImposition.none())
                        .build(userRegistry);

                return userRegistry.join(userRegistryOverrides);
            };
        }

        public Impositions getImpositions() {
            return impositions;
        }

        public ServerConfig getServerConfig() {
            return serverConfig;
        }

        public Function<? super Registry, ? extends Registry> getUserRegistryFactory() {
            return userRegistryFactory;
        }
    }

    protected DefaultGrpcRatpackServer.DefinitionBuild buildUserDefinition() throws Exception {
        return Impositions.impose(impositions, () -> {
            try {
                return new DefaultGrpcRatpackServer.DefinitionBuild(impositions, RatpackServerDefinition.build(definitionFactory), null);
            } catch (Exception e) {
                return new DefaultGrpcRatpackServer.DefinitionBuild(impositions, RatpackServerDefinition.build(s -> s.handler(r -> ctx -> ctx.error(e))), e);
            }
        });
    }

}
