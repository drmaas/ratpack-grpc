package ratpack.grpc;

public class GrpcConfig {

    private int port = 50051;
    private boolean useRatpackEventLoop = true;

    public int getPort() {
        return port;
    }

    public GrpcConfig port(int port) {
        this.port = port;
        return this;
    }

    public boolean isUseRatpackEventLoop() {
        return useRatpackEventLoop;
    }

    public GrpcConfig useRatpackEventLoop(boolean useRatpackEventLoop) {
        this.useRatpackEventLoop = useRatpackEventLoop;
        return this;
    }

}
