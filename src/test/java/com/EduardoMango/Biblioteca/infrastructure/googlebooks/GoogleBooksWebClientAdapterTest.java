package com.EduardoMango.Biblioteca.infrastructure.googlebooks;

import com.EduardoMango.Biblioteca.domain.book.dto.ExternalBookDto;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException.Failure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.SocketPolicy;
import com.EduardoMango.Biblioteca.infrastructure.googlebooks.adapter.out.GoogleBooksWebClientAdapter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GoogleBooksWebClientAdapterTest {

    private MockWebServer mockWebServer;
    private GoogleBooksWebClientAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        WebClient.Builder builder = WebClient.builder();
        adapter = new GoogleBooksWebClientAdapter(builder, baseUrl, "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Escenario 1: Búsqueda exitosa de libro por ISBN retorna ExternalBookDto completo")
    void testFindByIsbn_Success() throws InterruptedException {
        String jsonBody = """
                {
                  "kind": "books#volumes",
                  "totalItems": 1,
                  "items": [
                    {
                      "id": "abc123",
                      "volumeInfo": {
                        "title": "Effective Java",
                        "authors": ["Joshua Bloch"],
                        "publisher": "Addison-Wesley",
                        "publishedDate": "2018",
                        "description": "Best practices for Java",
                        "pageCount": 412,
                        "categories": ["Computers"],
                        "imageLinks": {
                          "smallThumbnail": "http://books.google.com/small.jpg",
                          "thumbnail": "http://books.google.com/thumb.jpg"
                        },
                        "industryIdentifiers": [
                          {
                            "type": "ISBN_13",
                            "identifier": "9780134685991"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody));

        StepVerifier.create(adapter.findByIsbn("9780134685991"))
                .assertNext(dto -> {
                    assertEquals("9780134685991", dto.isbn());
                    assertEquals("Effective Java", dto.title());
                    assertEquals(1, dto.authors().size());
                    assertEquals("Joshua Bloch", dto.authors().get(0));
                    assertEquals("Addison-Wesley", dto.publisher());
                    assertEquals("Best practices for Java", dto.description());
                    assertEquals("https://books.google.com/thumb.jpg", dto.coverUrl());
                    assertEquals(1, dto.categories().size());
                    assertEquals("Computers", dto.categories().get(0));
                })
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertTrue(recordedRequest.getPath().contains("/volumes?q=isbn:9780134685991&key=test-api-key"));
    }

    @Test
    @DisplayName("Escenario 4: Búsqueda de ISBN inexistente en Google Books retorna Mono.empty()")
    void testFindByIsbn_NotFoundReturnsEmpty() {
        String jsonBody = """
                {
                  "kind": "books#volumes",
                  "totalItems": 0
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody));

        StepVerifier.create(adapter.findByIsbn("0000000000000"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Búsqueda con respuesta 404 del servidor externo retorna Mono.empty()")
    void testFindByIsbn_Http404ReturnsEmpty() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(adapter.findByIsbn("1111111111111"))
                .verifyComplete();
    }

    static Stream<Arguments> providerErrors() {
        return Stream.of(
                Arguments.of(429, "{}", Failure.QUOTA_EXCEEDED),
                Arguments.of(500, "{}", Failure.UNAVAILABLE),
                Arguments.of(503, "{\"error\":{\"errors\":[{\"reason\":\"backendFailed\"}]}}", Failure.UNAVAILABLE),
                Arguments.of(401, "{}", Failure.CONFIGURATION_ERROR),
                Arguments.of(403, "{}", Failure.CONFIGURATION_ERROR),
                Arguments.of(400, "{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}", Failure.CONFIGURATION_ERROR),
                Arguments.of(403, "{\"error\":{\"errors\":[{\"reason\":\"dailyLimitExceeded\"}]}}", Failure.QUOTA_EXCEEDED),
                Arguments.of(403, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}", Failure.QUOTA_EXCEEDED),
                Arguments.of(403, "not json: test-api-key", Failure.CONFIGURATION_ERROR),
                Arguments.of(400, "{}", Failure.REQUEST_REJECTED),
                Arguments.of(302, "{}", Failure.REQUEST_REJECTED));
    }

    @ParameterizedTest
    @MethodSource("providerErrors")
    void errorsAreSafeAndNeverRetried(int status, String body, Failure expected) {
        mockWebServer.enqueue(new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setHeader("Retry-After", "60").setBody(body));
        StepVerifier.create(adapter.findByIsbn("9780134494166"))
                .expectErrorSatisfies(error -> {
                    GoogleBooksException safe = assertInstanceOf(GoogleBooksException.class, error);
                    assertEquals(expected, safe.getFailure());
                    assertEquals(status, safe.getUpstreamStatus());
                    assertEquals("60", safe.getRetryAfter());
                    assertNull(safe.getCause());
                    assertFalse(safe.getMessage().contains("test-api-key"));
                    assertFalse(safe.getMessage().contains("http"));
                }).verify(Duration.ofSeconds(5));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"totalItems\":1}", "{\"items\":[null]}", "{\"items\":[{}]}"})
    void invalidSuccessBodyIsNotReportedAsBookNotFound(String body) {
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
        StepVerifier.create(adapter.findByIsbn("9780134494166"))
                .expectErrorSatisfies(error -> assertEquals(Failure.INVALID_RESPONSE,
                        assertInstanceOf(GoogleBooksException.class, error).getFailure()))
                .verify(Duration.ofSeconds(5));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void incompatibleContentTypeIsReportedSafely() {
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<html>Service failure: test-api-key</html>"));
        StepVerifier.create(adapter.findByIsbn("9780134494166"))
                .expectErrorSatisfies(error -> {
                    assertEquals(Failure.INVALID_RESPONSE,
                            assertInstanceOf(GoogleBooksException.class, error).getFailure());
                    assertFalse(error.getMessage().contains("test-api-key"));
                }).verify(Duration.ofSeconds(5));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void slowResponseTimesOutWithoutRetry() {
        adapter = new GoogleBooksWebClientAdapter(WebClient.builder(), mockWebServer.url("/").toString(),
                "test-api-key", Duration.ofMillis(250));
        mockWebServer.enqueue(new MockResponse().setHeadersDelay(1, TimeUnit.SECONDS)
                .setHeader("Content-Type", "application/json").setBody("{\"totalItems\":0}"));
        StepVerifier.create(adapter.findByIsbn("9780134494166"))
                .expectErrorSatisfies(error -> assertEquals(Failure.TIMEOUT,
                        assertInstanceOf(GoogleBooksException.class, error).getFailure()))
                .verify(Duration.ofSeconds(5));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void disconnectedTransportDoesNotReplayTheRequest() {
        mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        StepVerifier.create(adapter.findByIsbn("9780134494166"))
                .expectErrorSatisfies(error -> assertEquals(Failure.UNAVAILABLE,
                        assertInstanceOf(GoogleBooksException.class, error).getFailure()))
                .verify(Duration.ofSeconds(5));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void validHttpDateIsPreserved() {
        String date = "Wed, 21 Oct 2037 07:28:00 GMT";
        GoogleBooksException error = new GoogleBooksException(Failure.UNAVAILABLE, 503, date);
        assertEquals(date, error.getRetryAfter());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tomorrow", "-1", "99999999999", "test-api-key", "60\r\nX-Injected: value"})
    void invalidRetryAfterIsDiscarded(String value) {
        assertNull(new GoogleBooksException(Failure.UNAVAILABLE, 503, value).getRetryAfter());
    }
}

