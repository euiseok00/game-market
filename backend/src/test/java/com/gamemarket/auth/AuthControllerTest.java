package com.gamemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    private ResultActions signup(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private static String uniqueName() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void signup_duplicateUsername_returns409() throws Exception {
        String name = uniqueName();
        signup(name, "secret123").andExpect(status().isCreated());
        signup(name, "other123").andExpect(status().isConflict());
    }

    @Test
    void signup_blankPassword_returns400() throws Exception {
        signup(uniqueName(), " ").andExpect(status().isBadRequest());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String name = uniqueName();
        signup(name, "secret123").andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong\"}".formatted(name)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void password_isStoredAsBcryptHash() throws Exception {
        String name = uniqueName();
        signup(name, "secret123").andExpect(status().isCreated());
        String stored = users.findByUsername(name).orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo("secret123").startsWith("$2");
    }

    @Test
    void cart_withoutLogin_returns401() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }
}
