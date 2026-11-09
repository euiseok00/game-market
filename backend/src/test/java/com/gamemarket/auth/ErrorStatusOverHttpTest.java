package com.gamemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamemarket.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/** Real server: MockMvc skips the /error dispatch, which is where anonymous error statuses got lost. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ErrorStatusOverHttpTest extends IntegrationTestBase {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private int get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int signup(String username, String password) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void sessionCookie_isSameSiteLax() throws Exception {
        String name = "h" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(signup(name, "secret123")).isEqualTo(201);
        HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(name))).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().allValues("Set-Cookie"))
                .anySatisfy(c -> assertThat(c).startsWith("JSESSIONID=").contains("SameSite=Lax"));
    }

    @Test
    void anonymous_unknownGame_returns404() throws Exception {
        assertThat(get("/api/games/999999")).isEqualTo(404);
    }

    @Test
    void anonymous_duplicateSignup_returns409() throws Exception {
        String name = "h" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(signup(name, "secret123")).isEqualTo(201);
        assertThat(signup(name, "secret123")).isEqualTo(409);
    }

    @Test
    void anonymous_blankSignup_returns400() throws Exception {
        assertThat(signup("", "")).isEqualTo(400);
    }

    @Test
    void signup_passwordOver72Bytes_returns400() throws Exception {
        String name = "h" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(signup(name, "a".repeat(73))).isEqualTo(400);
        assertThat(signup(name, "가".repeat(25))).isEqualTo(400); // 75 UTF-8 bytes, 25 chars
    }
}
