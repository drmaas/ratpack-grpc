package ratpack.grpc.server

import com.google.inject.Scopes
import io.grpc.stub.StreamObserver
import ratpack.grpc.helloworld.GreeterGrpc
import ratpack.grpc.helloworld.HelloReply
import ratpack.grpc.helloworld.HelloRequest
import kotlin.test.Test
import ratpack.guice.Guice
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFails
import kotlin.test.assertTrue

class GrpcRatpackServerTest {

    var server: GrpcRatpackServer? = null

    @BeforeTest
    fun setup() {
        server = GrpcRatpackServer.start {
            it.registry(Guice.registry {
               it.bind(GreeterService::class.java)
            })
        }
    }

    @AfterTest
    fun cleanup() {
        server?.stop()
    }

    @Test
    fun `test grpc ratpack server`() {
        assertTrue(server?.isRunning ?: false, "server is not running")
    }

}

class GreeterService : GreeterGrpc.GreeterImplBase() {
    override fun sayHello(req: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        val reply = HelloReply.newBuilder().setMessage("Hello ${req.name}").build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}