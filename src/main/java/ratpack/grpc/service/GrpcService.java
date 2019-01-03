package ratpack.grpc.service;

import com.google.common.collect.Lists;
import io.grpc.BindableService;
import ratpack.exec.ExecController;
import ratpack.grpc.GrpcConfig;
import ratpack.grpc.server.GrpcServer;
import ratpack.registry.Registry;
import ratpack.server.ServerConfig;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import java.util.List;

public class GrpcService implements Service {

    private GrpcServer server;

    @Override
    public void onStart(StartEvent event) throws Exception {
        Registry registry = event.getRegistry();
        ExecController execController = registry.get(ExecController.class);
        List<BindableService> services = Lists.newArrayList(registry.getAll(BindableService.class));
        ServerConfig serverConfig = registry.get(ServerConfig.class);
        GrpcConfig config = registry.get(GrpcConfig.class);
        this.server = new GrpcServer(execController, services, serverConfig, config).start();
    }

    @Override
    public void onStop(StopEvent event) {
        server.stop();
    }
}
