package com.gamemarket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public abstract class ApiTestBase extends IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected GameRepository games;

    /** Signs up a fresh user and returns a logged-in session. */
    protected MockHttpSession newUserSession() throws Exception {
        String body = "{\"username\":\"u%s\",\"password\":\"secret123\"}"
                .formatted(UUID.randomUUID().toString().substring(0, 8));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        return session;
    }

    protected List<Game> paidGames() {
        return games.findAll().stream().filter(g -> g.getPriceKrw() > 0).toList();
    }
}
