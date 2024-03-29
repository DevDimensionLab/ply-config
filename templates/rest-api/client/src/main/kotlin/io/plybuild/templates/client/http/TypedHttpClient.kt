package io.plybuild.templates.client.http

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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


open class TypedHttpClient(
    private val baseUrl: String,
    private val timeout: Duration = Duration.ofSeconds(15),
    private val httpClient: HttpClient = HttpClient.newBuilder().version(HTTP_1_1).build(),
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
) {
    private val log: Logger = LoggerFactory.getLogger(TypedHttpClient::class.java)

    fun <T> getEntity(path: String, responseType: Class<T>): TypedResponse<T> =
        getEntity(path, objectMapper.typeFactory.constructType(responseType))

    fun <T> getEntity(path: String, responseType: TypeReference<T>): TypedResponse<T> =
        getEntity(path, objectMapper.typeFactory.constructType(responseType))

    fun <T> getEntities(path: String, responseType: Class<T>): TypedResponse<List<T>> =
        getEntity(path, objectMapper.typeFactory.constructCollectionType(List::class.java, responseType))

    private fun <T> getEntity(path: String, responseType: JavaType): TypedResponse<T> {

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
            onSuccess = { httpResponse ->
                log.info("$uri ${httpResponse.statusCode()} ${elapsedTime}ms ")
                TypedResponse.create(objectMapper, httpResponse, responseType)
            },
            onFailure = { exception ->
                throw HttpException(
                    message = "$uri ${elapsedTime}ms - $exception",
                    cause = exception
                )
            }
        )
    }
}

