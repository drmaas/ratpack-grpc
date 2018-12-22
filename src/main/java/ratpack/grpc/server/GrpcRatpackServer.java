package ratpack.grpc.server;

import ratpack.func.Action;
import ratpack.grpc.server.internal.DefaultGrpcRatpackServer;
import ratpack.impose.Impositions;
import ratpack.server.RatpackServer;
import ratpack.server.RatpackServerSpec;

public interface GrpcRatpackServer extends RatpackServer {

    /**
     * Creates a new, unstarted, Ratpack server from the given definition.
     * <p>
     * The action argument effectively serves as the definition of the server.
     * It receives a mutable server builder style object, a {@link RatpackServerSpec}.
     * The action is retained internally by the server, and invoked again if the {@link #reload()} method is called.
     *
     * @param definition the server definition
     * @return a Ratpack server
     * @see RatpackServerSpec
     * @throws Exception any thrown by creating the server
     */
    static GrpcRatpackServer of(Action<? super RatpackServerSpec> definition) throws Exception {
        return new DefaultGrpcRatpackServer(definition, Impositions.current());
    }

    /**
     * Convenience method to {@link #of(Action) define} and {@link #start()} the server in one go.
     *
     * @param definition the server definition
     * @return the newly created and started server
     * @throws Exception any thrown by {@link #of(Action)} or {@link #start()}
     */
    static GrpcRatpackServer start(Action<? super RatpackServerSpec> definition) throws Exception {
        GrpcRatpackServer server = of(definition);
        server.start();
        return server;
    }

}
