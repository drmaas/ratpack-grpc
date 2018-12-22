package ratpack.grpc.core;

import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.api.Nullable;
import ratpack.exec.ExecController;
import ratpack.grpc.config.GrpcConfig;
import ratpack.server.ServerConfig;
import ratpack.util.internal.TransportDetector;

import java.net.InetSocketAddress;
import java.util.List;

public class GrpcServer {

    private ExecController execController;
    private List<ServerServiceDefinition> services;
    private ServerConfig serverConfig;
    private GrpcConfig config;

    private InetSocketAddress address;
    private Server server;

    @Nullable
    private Thread shutdownHookThread;

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    public GrpcServer(ExecController execController, List<ServerServiceDefinition> services, ServerConfig serverConfig, GrpcConfig config) {
        this.execController = execController;
        this.services = services;
        this.serverConfig = serverConfig;
        this.config = config;
    }

    // TODO get all serverConfig options and set grpc builder values
    public GrpcServer start() throws Exception {
        int port = config.getPort();
        address = new InetSocketAddress(port);
        NettyServerBuilder serverBuilder = NettyServerBuilder.forAddress(address)
                .channelType(TransportDetector.getServerSocketChannelImpl());
        if (config.isUseRatpackEventLoop()) {
            serverBuilder.executor(execController.getExecutor());
        }
        if (serverConfig.getNettySslContext() != null) {
            serverBuilder.sslContext(serverConfig.getNettySslContext());
        }
        services.forEach(serverBuilder::addService);
        server = serverBuilder.build().start();
        logger.info("gRPC server started, listening on " + port);

        shutdownHookThread = new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            logger.info("shutting down gRPC server since JVM is shutting down");
            server.shutdownNow();
        }, "grpc-shutdown-thread");
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        return this;
    }

    public GrpcServer stop() {
        if (server != null) {
            try {
                if (shutdownHookThread != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
                }
            } catch (Exception ignored) {
                // just ignore
            }
            server.shutdown();
            logger.info("gRPC server stopped");
        }
        return this;
    }

    public InetSocketAddress address() {
        return address;
    }

    public int port() {
        return address.getPort();
    }

    public boolean isRunning() {
        return !server.isShutdown();
    }
}
