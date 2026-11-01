package com.EduardoMango.Biblioteca.infrastructure.googlebooks.adapter.out;

import com.EduardoMango.Biblioteca.domain.book.dto.ExternalBookDto;
import com.EduardoMango.Biblioteca.domain.book.port.out.ExternalBookSearchPort;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException;
import com.EduardoMango.Biblioteca.exception.GoogleBooksException.Failure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.EduardoMango.Biblioteca.infrastructure.googlebooks.dto.GoogleBookIndustryIdentifier;
import com.EduardoMango.Biblioteca.infrastructure.googlebooks.dto.GoogleBooksResponse;
import com.EduardoMango.Biblioteca.infrastructure.googlebooks.dto.GoogleBookVolumeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class GoogleBooksWebClientAdapter implements ExternalBookSearchPort {

    private final WebClient webClient;
    private final String apiKey;
    private final Duration requestTimeout;
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
    private static final Set<String> QUOTA_REASONS = Set.of(
            "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded",
            "dailyLimitExceededUnreg", "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED");
    private static final Set<String> CONFIGURATION_REASONS = Set.of(
            "keyInvalid", "accessNotConfigured", "ipRefererBlocked", "API_KEY_INVALID",
            "API_KEY_SERVICE_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
            "SERVICE_DISABLED", "API_KEY_EXPIRED");

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleBooksWebClientAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${google.books.api.base-url:https://www.googleapis.com/books/v1}") String baseUrl,
            @Value("${google.books.api.key:}") String apiKey,
            @Value("${google.books.api.timeout:10s}") Duration requestTimeout) {
        this.webClient = webClientBuilder.baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create().disableRetry(true)))
                .build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Google Books timeout must be positive");
        }
        this.requestTimeout = requestTimeout;
    }

    public GoogleBooksWebClientAdapter(WebClient.Builder builder, String baseUrl, String apiKey) {
        this(builder, baseUrl, apiKey, Duration.ofSeconds(10));
    }

    public GoogleBooksWebClientAdapter(WebClient webClient, String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.requestTimeout = Duration.ofSeconds(10);
    }

    @Override
    public Mono<ExternalBookDto> findByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return Mono.empty();
        }

        String cleanIsbn = isbn.trim();

        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/volumes")
                            .queryParam("q", "isbn:" + cleanIsbn);
                    if (!apiKey.isBlank()) {
                        builder.queryParam("key", apiKey);
                    }
                    return builder.build();
                })
                .exchangeToMono(this::readResponse)
                .timeout(requestTimeout)
                // Do not retain transport exceptions: their messages can include the API key.
                .onErrorMap(TimeoutException.class, e -> failure(Failure.TIMEOUT, null, null))
                .onErrorMap(WebClientRequestException.class, e -> failure(Failure.UNAVAILABLE, null, null))
                .onErrorMap(DecodingException.class, e -> failure(Failure.INVALID_RESPONSE, null, null))
                .onErrorMap(org.springframework.web.reactive.function.UnsupportedMediaTypeException.class,
                        e -> failure(Failure.INVALID_RESPONSE, null, null))
                .onErrorMap(DataBufferLimitException.class, e -> failure(Failure.INVALID_RESPONSE, null, null))
                .onErrorMap(WebClientResponseException.class, e -> failure(Failure.INVALID_RESPONSE, null, null))
                .flatMap(response -> {
                    if (response == null || response.items() == null || response.items().isEmpty()) {
                        if (response == null || response.totalItems() == null || response.totalItems() != 0) {
                            return Mono.error(failure(Failure.INVALID_RESPONSE, 200, null));
                        }
                        log.info("No external book found in Google Books for ISBN: {}", cleanIsbn);
                        return Mono.empty();
                    }

                    if (response.items().get(0) == null || response.items().get(0).volumeInfo() == null) {
                        return Mono.error(failure(Failure.INVALID_RESPONSE, 200, null));
                    }
                    GoogleBookVolumeInfo volumeInfo = response.items().get(0).volumeInfo();

                    return Mono.just(mapToExternalBookDto(cleanIsbn, volumeInfo));
                });
    }

    private Mono<GoogleBooksResponse> readResponse(ClientResponse response) {
        int status = response.statusCode().value();
        String retryAfter = response.headers().asHttpHeaders().getFirst("Retry-After");
        if (status == 404) return response.releaseBody().then(Mono.empty());
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(GoogleBooksResponse.class)
                    .switchIfEmpty(Mono.error(failure(Failure.INVALID_RESPONSE, status, null)));
        }
        if (status == 400 || status == 403) {
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(body -> Mono.error(failure(classifyRejection(status, body), status, retryAfter)));
        }
        Failure kind = status == 429 ? Failure.QUOTA_EXCEEDED
                : status == 401 ? Failure.CONFIGURATION_ERROR
                : response.statusCode().is5xxServerError() ? Failure.UNAVAILABLE : Failure.REQUEST_REJECTED;
        return response.releaseBody().then(Mono.error(failure(kind, status, retryAfter)));
    }

    private Failure classifyRejection(int status, String body) {
        try {
            JsonNode error = ERROR_MAPPER.readTree(body).path("error");
            if (QUOTA_REASONS.contains(error.path("status").asText())) return Failure.QUOTA_EXCEEDED;
            for (String field : List.of("errors", "details")) {
                for (JsonNode detail : error.path(field)) {
                    if (QUOTA_REASONS.contains(detail.path("reason").asText())) return Failure.QUOTA_EXCEEDED;
                }
            }
            for (String field : List.of("errors", "details")) {
                for (JsonNode detail : error.path(field)) {
                    if (CONFIGURATION_REASONS.contains(detail.path("reason").asText())) return Failure.CONFIGURATION_ERROR;
                }
            }
        } catch (Exception ignored) {
            // Provider bodies are untrusted; never log or return them.
        }
        return status == 403 ? Failure.CONFIGURATION_ERROR : Failure.REQUEST_REJECTED;
    }

    private GoogleBooksException failure(Failure kind, Integer status, String retryAfter) {
        return new GoogleBooksException(kind, status, retryAfter);
    }

    private ExternalBookDto mapToExternalBookDto(String fallbackIsbn, GoogleBookVolumeInfo volumeInfo) {
        String resolvedIsbn = resolveIsbn(volumeInfo, fallbackIsbn);
        String title = volumeInfo.title() != null ? volumeInfo.title() : "Sin título";
        List<String> authors = volumeInfo.authors() != null ? volumeInfo.authors() : Collections.emptyList();
        String publisher = volumeInfo.publisher();
        String description = volumeInfo.description();
        String coverUrl = resolveCoverUrl(volumeInfo);
        List<String> categories = volumeInfo.categories() != null ? volumeInfo.categories() : Collections.emptyList();
        Integer pageCount = volumeInfo.pageCount();
        String publishedDate = volumeInfo.publishedDate();

        return new ExternalBookDto(
                resolvedIsbn,
                title,
                authors,
                publisher,
                description,
                coverUrl,
                categories,
                pageCount,
                publishedDate
        );
    }

    private String resolveIsbn(GoogleBookVolumeInfo volumeInfo, String fallbackIsbn) {
        if (volumeInfo.industryIdentifiers() != null && !volumeInfo.industryIdentifiers().isEmpty()) {
            for (GoogleBookIndustryIdentifier id : volumeInfo.industryIdentifiers()) {
                if ("ISBN_13".equalsIgnoreCase(id.type())) {
                    return id.identifier();
                }
            }
            for (GoogleBookIndustryIdentifier id : volumeInfo.industryIdentifiers()) {
                if ("ISBN_10".equalsIgnoreCase(id.type())) {
                    return id.identifier();
                }
            }
        }
        return fallbackIsbn;
    }

    private String resolveCoverUrl(GoogleBookVolumeInfo volumeInfo) {
        if (volumeInfo.imageLinks() == null) {
            return null;
        }
        String cover = volumeInfo.imageLinks().thumbnail();
        if (cover == null || cover.isBlank()) {
            cover = volumeInfo.imageLinks().smallThumbnail();
        }
        if (cover != null && cover.startsWith("http://")) {
            cover = cover.replace("http://", "https://");
        }
        return cover;
    }
}
