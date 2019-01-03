package ratpack.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import ratpack.grpc.helloworld.GreeterGrpc
import ratpack.grpc.helloworld.HelloReply
import ratpack.grpc.helloworld.HelloRequest
import java.util.concurrent.TimeUnit

class GreeterClient internal constructor(private val channel: ManagedChannel) {

    companion object {
        private val logger = LoggerFactory.getLogger(GreeterClient::class.java)
    }

    private val blockingStub: GreeterGrpc.GreeterBlockingStub by lazy {
        GreeterGrpc.newBlockingStub(channel)
    }

    constructor(host: String, port: Int) : this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build())


    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    fun greet(name: String): String {
        logger.info("Will try to greet $name...")
        val request = HelloRequest.newBuilder().setName(name).build()
        val response: HelloReply =  try {
            blockingStub.sayHello(request)
        } catch (e: StatusRuntimeException) {
            e.printStackTrace()
            logger.warn("RPC failed: ${e.status}")
            return ""
        }

        logger.info("Greeting: ${response.message}")
        return response.message
    }
}

class GreeterService : GreeterGrpc.GreeterImplBase() {
    override fun sayHello(req: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        val reply = HelloReply.newBuilder().setMessage("Hello ${req.name}").build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}