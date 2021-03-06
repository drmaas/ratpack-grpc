package ratpack.grpc.server

import ratpack.grpc.GreeterClient
import ratpack.grpc.GreeterService
import ratpack.guice.Guice
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GrpcRatpackServerTest {

    var server: GrpcRatpackServer? = null
    var client: GreeterClient? = null

    @BeforeTest
    fun setup() {
        val port = 32768
        client = GreeterClient("localhost", port)
        server = GrpcRatpackServer.start {
            it.serverConfig {
                it.port(port)
            }
            it.registry(Guice.registry {
                it.bind(GreeterService::class.java)
            })
        }
    }

    @AfterTest
    fun cleanup() {
        server?.stop()
        client?.shutdown()
    }

    @Test
    fun `test grpc ratpack server`() {
        assertTrue(server?.isRunning ?: false, "server is not running")
        val user = "drmaas"
        val response = client?.greet(user)
        assert(response == "Hello drmaas")
    }

}