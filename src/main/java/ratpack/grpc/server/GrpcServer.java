package ratpack.grpc.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.api.Nullable;
import ratpack.exec.ExecController;
import ratpack.grpc.GrpcConfig;
import ratpack.server.ServerConfig;
import ratpack.util.internal.TransportDetector;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GrpcServer {

    private ExecController execController;
    private List<BindableService> services;
    private ServerConfig serverConfig;
    private GrpcConfig config;

    private InetSocketAddress address;
    private Server server;

    @Nullable
    private Thread shutdownHookThread;

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    public GrpcServer(ExecController execController, List<BindableService> services, ServerConfig serverConfig, GrpcConfig config) {
        this.execController = execController;
        this.services = services;
        this.serverConfig = serverConfig;
        this.config = config;
    }

    public GrpcServer start() throws Exception {
        int port = config.getPort();
        address = new InetSocketAddress(port);
        NettyServerBuilder serverBuilder = NettyServerBuilder.forAddress(address)
                .channelType(TransportDetector.getServerSocketChannelImpl())
                .withChildOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

        // custom grpc ratpack configurations
        if (config.isUseRatpackEventLoop()) {
            // worker and boss can be the same, see https://groups.google.com/forum/#!topic/grpc-io/LrnAbWFozb0
            serverBuilder.bossEventLoopGroup(execController.getEventLoopGroup());
            serverBuilder.workerEventLoopGroup(execController.getEventLoopGroup());
            serverBuilder.executor(execController.getExecutor());
        }

        // standard ratpack configurations
        if (serverConfig.getNettySslContext() != null) {
            serverBuilder.sslContext(serverConfig.getNettySslContext());
        }
        serverConfig.getConnectTimeoutMillis().ifPresent(i ->
                serverBuilder.withChildOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, i)
        );
        serverConfig.getMaxMessagesPerRead().ifPresent(i -> {
            FixedRecvByteBufAllocator allocator = new FixedRecvByteBufAllocator(i);
            serverBuilder.withChildOption(ChannelOption.RCVBUF_ALLOCATOR, allocator);
        });
        serverConfig.getReceiveBufferSize().ifPresent(i ->
                serverBuilder.withChildOption(ChannelOption.SO_RCVBUF, i)
        );
        serverConfig.getWriteSpinCount().ifPresent(i ->
                serverBuilder.withChildOption(ChannelOption.WRITE_SPIN_COUNT, i)
        );
        serverConfig.getConnectQueueSize().ifPresent(i ->
                serverBuilder.withChildOption(ChannelOption.SO_BACKLOG, i)
        );
        Duration idle = serverConfig.getIdleTimeout();
        if (!idle.isZero() && !idle.isNegative()) {
            serverBuilder.maxConnectionIdle(idle.getNano(), TimeUnit.NANOSECONDS);
            serverBuilder.maxHeaderListSize(serverConfig.getMaxHeaderSize());
        }

        // services
        services.forEach(serverBuilder::addService);

        // start server
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
