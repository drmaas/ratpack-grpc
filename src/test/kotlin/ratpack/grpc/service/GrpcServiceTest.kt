package ratpack.grpc.service

import ratpack.grpc.GreeterClient
import ratpack.grpc.GreeterService
import ratpack.grpc.GrpcModule
import ratpack.guice.Guice
import ratpack.server.RatpackServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GrpcServiceTest {

    var server: RatpackServer? = null
    var client: GreeterClient? = null

    @BeforeTest
    fun setup() {
        val port = 32768
        client = GreeterClient("localhost", port)
        server = RatpackServer.start {
            it.registry(Guice.registry {
                it.bind(GreeterService::class.java)
                it.module(GrpcModule::class.java) {
                    it.port(port)
                    it.useRatpackEventLoop(false)
                }
            })
        }
    }

    @AfterTest
    fun cleanup() {
        server?.stop()
        client?.shutdown()
    }

    @Test
    fun `test grpc server`() {
        val user = "drmaas"
        val response = client?.greet(user)
        assert(response == "Hello drmaas")
    }

}