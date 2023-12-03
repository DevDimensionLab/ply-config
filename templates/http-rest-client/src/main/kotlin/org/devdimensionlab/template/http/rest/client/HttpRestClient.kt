package org.devdimensionlab.template.http.rest.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.System.currentTimeMillis
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Version.HTTP_1_1
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

class HttpRestClient(
    private val baseUrl: String,
    private val timeout: Duration = Duration.ofSeconds(15),
    private val httpClient: HttpClient = HttpClient.newBuilder().version(HTTP_1_1).build(),
    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())
) {
    private val log: Logger = LoggerFactory.getLogger(HttpRestClient::class.java)

    fun <T> getEntity(path: String, responseType: Class<T>): HttpRestResponse<T> {

        val uri = URI(baseUrl + path)
        val httpRequest: HttpRequest = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .GET()
            .build()

        val start = currentTimeMillis()
        val response: Result<HttpResponse<String>> = httpClient.runCatching {
            send(httpRequest) { HttpResponse.BodySubscribers.ofString(UTF_8) }
        }
        val elapsedTime = currentTimeMillis().minus(start)

        return response.fold(
            onSuccess = {
                log.info("$uri ${it.statusCode()} ${elapsedTime}ms ")
                HttpRestResponse(
                    status = it.statusCode(),
                    bodyFunc = { objectMapper.readValue(it.body(), responseType) })
            },
            onFailure = {
                throw HttpRestClientError("$uri ${elapsedTime}ms - $it", it)
            }
        )
    }
}