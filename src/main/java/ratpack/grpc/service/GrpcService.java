package ratpack.grpc.service;

import io.grpc.ServerServiceDefinition;
import ratpack.exec.ExecController;
import ratpack.grpc.config.GrpcConfig;
import ratpack.grpc.core.GrpcServer;
import ratpack.server.ServerConfig;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import javax.inject.Inject;
import java.util.List;

public class GrpcService implements Service {

    private ExecController execController;
    private List<ServerServiceDefinition> services;
    private GrpcConfig config;
    private ServerConfig serverConfig;

    private GrpcServer server;

    @Inject
    public GrpcService(ExecController execController, List<ServerServiceDefinition> services, ServerConfig serverConfig, GrpcConfig config) {
        this.execController = execController;
        this.services = services;
        this.serverConfig = serverConfig;
        this.config = config;
    }

    @Override
    public void onStart(StartEvent event) throws Exception {
        server = new GrpcServer(execController, services, serverConfig, config).start();
    }

    @Override
    public void onStop(StopEvent event) {
        server.stop();
    }
}
