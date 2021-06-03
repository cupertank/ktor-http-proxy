package me.cupertank

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class HttpRequest(
    val requestBytes: ByteArray,
    val method: String,
    val host: String,
    val port: Int,
) {
    companion object {
        fun fromBytes(bytes: ByteArray): HttpRequest {
            try {
                val string = bytes.toString(Charsets.UTF_8)
//                println(string)
                val lines = string.lines()
                val firstLineArgs = lines.first().split(" ")

                val method = firstLineArgs[0]
                val hostLine = lines.find { it.toLowerCase().startsWith("host:") } ?: ""
                val hostLineArgs = hostLine.split(":")

                return if (hostLineArgs.size == 3) {
                    val (_, host, port) = hostLine.split(":")
                    HttpRequest(bytes, method, host.trim(), port.toInt())
                } else {
                    val (_, host) = hostLine.split(":")
                    HttpRequest(bytes, method, host.trim(), 80)
                }
            } catch (E: Exception) {
                return EMPTY
            }
        }

        val EMPTY = HttpRequest(ByteArray(0), "", "", -1)
    }
}


fun main(args: Array<String>) {
    val argParser = ArgParser("ktor-http-server")
    val port by argParser.option(ArgType.Int, "port", "p", "Proxy server port").default(8080)
    val hostName by argParser.option(ArgType.String, "hostname", "host", "Proxy server hostname").default("0.0.0.0")
    argParser.parse(args)


    runBlocking {
        val selector = ActorSelectorManager(Dispatchers.Default)
        val builder = aSocket(selector).tcp()

        val server = builder.bind(hostName, port) {
            reuseAddress = true
            reusePort = true
        }
        while (true) {
            val clientSocket = server.accept()

            launch {
                try {
                    val buffer = ByteArray(8192)
                    println("Socket accepted: ${clientSocket.remoteAddress}")

                    val clientReader = clientSocket.openReadChannel()
                    val clientWriter = clientSocket.openWriteChannel(autoFlush = true)

                    clientReader.awaitContent()
                    var size = clientReader.readAvailable(buffer)

                    val httpRequest = HttpRequest.fromBytes(buffer)
                    if (buffer.isEmpty() || httpRequest == HttpRequest.EMPTY) {
                        return@launch
                    }

                    println("HOST: ${httpRequest.host}:${httpRequest.port}")

                    if (httpRequest.method == "CONNECT") {
                        val serverSocket = try {
                            builder.connect(httpRequest.host, httpRequest.port)
                        } catch (e: Exception) {
                            println("Failed CONNECT")
                            return@launch
                        }

                        val serverReader = serverSocket.openReadChannel()
                        val serverWriter = serverSocket.openWriteChannel(autoFlush = true)

                        clientWriter.writeAvailable("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                        clientWriter.flush()

                        while (!clientReader.isClosedForRead && !clientWriter.isClosedForWrite
                            && !serverReader.isClosedForRead && !serverReader.isClosedForWrite &&
                            !clientSocket.isClosed && !serverSocket.isClosed
                        ) {
                            delay(50)
                            while (clientReader.availableForRead > 0) {
                                println("CLIENT = ${clientReader.availableForRead}")
                                val size = clientReader.readAvailable(buffer)
                                serverWriter.writeFully(buffer, 0, size)
                                delay(25)
                            }
                            serverWriter.flush()

                            delay(50)
                            while (serverReader.availableForRead > 0) {
                                println("SERVER = ${serverReader.availableForRead}")
                                val size = serverReader.readAvailable(buffer)
                                clientWriter.writeFully(buffer, 0, size)
                                delay(25)
                            }
                            clientWriter.flush()
                        }
                    } else {
                        val serverSocket = builder.connect(httpRequest.host, httpRequest.port)
                        val serverWriter = serverSocket.openWriteChannel(autoFlush = true)
                        val serverReader = serverSocket.openReadChannel()

                        serverWriter.writeAvailable(buffer, 0, size)
                        serverWriter.flush()

                        serverReader.awaitContent()
                        size = serverReader.readAvailable(buffer)

                        clientWriter.writeAvailable(buffer, 0, size)
                        clientWriter.flush()

                    }
                } catch (e: Exception) {
                }
            }
        }
    }
}
