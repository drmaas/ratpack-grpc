package ratpack.grpc.config;

import com.google.inject.Scopes;
import ratpack.grpc.service.GrpcService;
import ratpack.guice.ConfigurableModule;

public class GrpcModule extends ConfigurableModule<GrpcConfig> {

    @Override
    protected void configure() {
        bind(GrpcService.class).in(Scopes.SINGLETON);
    }
}
