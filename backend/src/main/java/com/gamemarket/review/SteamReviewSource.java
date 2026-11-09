package com.gamemarket.review;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steam public review endpoint. Throws when Steam does not answer with JSON (it currently redirects to the
 * store home page); the caller falls back to seed reviews. No header spoofing by design.
 */
@Component
public class SteamReviewSource implements ReviewSource {

    private static final String URL =
            "https://store.steampowered.com/appreviews/{appId}?json=1&language=english&num_per_page=20&filter=recent";

    private final RestClient http;
    private final JsonMapper json;

    public SteamReviewSource(RestClient.Builder builder, JsonMapper json) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(factory).build();
        this.json = json;
    }

    @Override
    public List<String> fetch(long steamAppId) {
        String body = http.get().uri(URL, steamAppId).exchange((req, res) -> {
            MediaType type = res.getHeaders().getContentType();
            if (!res.getStatusCode().is2xxSuccessful() || type == null || !type.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                throw new IllegalStateException("Steam returned " + res.getStatusCode() + " " + type);
            }
            return new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        });
        JsonNode root = json.readTree(body);
        if (root.path("success").asInt() != 1) {
            throw new IllegalStateException("Steam success != 1");
        }
        return root.path("reviews").valueStream()
                .map(r -> r.path("review").asString(""))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
