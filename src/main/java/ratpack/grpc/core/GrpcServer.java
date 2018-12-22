package ratpack.grpc.core;

import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.ExecController;
import ratpack.grpc.config.GrpcConfig;
import ratpack.server.ServerConfig;

import java.net.InetSocketAddress;
import java.util.List;

public class GrpcServer {

    private ExecController execController;
    private List<ServerServiceDefinition> services;
    private ServerConfig serverConfig;
    private GrpcConfig config;

    private InetSocketAddress address;
    private Server server;

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    public GrpcServer(ExecController execController, List<ServerServiceDefinition> services, ServerConfig serverConfig, GrpcConfig config) {
        this.execController = execController;
        this.services = services;
        this.serverConfig = serverConfig;
        this.config = config;
    }

    public GrpcServer start() throws Exception {
        int port = config.getPort();
        address = new InetSocketAddress(port);
        NettyServerBuilder serverBuilder = NettyServerBuilder.forAddress(address);
        if (config.isUseRatpackEventLoop()) {
            serverBuilder.executor(execController.getExecutor());
        }
        if (serverConfig.getNettySslContext() != null) {
            serverBuilder.sslContext(serverConfig.getNettySslContext());
        }
        services.forEach(serverBuilder::addService);
        server = serverBuilder.build().start();
        logger.info("gRPC server started, listening on " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            server.shutdownNow();
            System.err.println("*** server shut down");
        }, "grpc-shutdown-thread"));
        return this;
    }

    public GrpcServer stop() {
        if (server != null) {
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
