package com.gamemarket.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.ApiTestBase;
import com.gamemarket.game.Game;
import com.gamemarket.review.ReviewSource;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Exercises the real GeminiSummarizer against a mocked ChatModel. */
@TestPropertySource(properties = {"summary.retry.initial-backoff-ms=1", "summary.llm-timeout-ms=300"})
@Import(SummaryFailureTest.FakeChat.class)
class SummaryFailureTest extends ApiTestBase {

    /** Scriptable ChatModel; a Mockito mock cannot supply default options before ChatClient captures them. */
    @TestConfiguration
    static class FakeChat {

        @Bean
        @Primary
        FakeChatModel fakeChatModel() {
            return new FakeChatModel();
        }
    }

    static class FakeChatModel implements ChatModel {

        final AtomicInteger calls = new AtomicInteger();
        final Deque<Supplier<ChatResponse>> script = new ArrayDeque<>();
        Supplier<ChatResponse> fallback = () -> reply("{}");

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            Supplier<ChatResponse> next = script.isEmpty() ? fallback : script.poll();
            return next.get();
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }

        void always(Supplier<ChatResponse> s) {
            script.clear();
            fallback = s;
        }
    }

    @Autowired
    FakeChatModel chatModel;

    @MockitoBean
    ReviewSource reviews;

    @Autowired
    SummaryRepository summaries;

    MockHttpSession session;
    Game game;

    @BeforeEach
    void setUp() throws Exception {
        session = newUserSession();
        game = games.findAll().get(2);
        summaries.findByGameId(game.getId()).ifPresent(summaries::delete);
        when(reviews.fetch(anyLong())).thenReturn(List.of("great combat", "too short"));
        chatModel.calls.set(0);
        chatModel.script.clear();
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void malformedJson_returns503_andStoresNothing() throws Exception {
        chatModel.always(() -> reply("this is not json"));
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
        assertThat(chatModel.calls.get()).isEqualTo(1); // parse failures are not retried
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }

    @Test
    void emptyResponse_returns503() throws Exception {
        chatModel.always(() -> reply(""));
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable());
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }

    @Test
    void rateLimit429_retriesThenReturns503() throws Exception {
        chatModel.always(() -> { throw new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded"); });
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
        assertThat(chatModel.calls.get()).isEqualTo(4); // 1 attempt + 3 retries
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }

    @Test
    void rateLimit429_thenSuccess_returnsSummary() throws Exception {
        chatModel.always(() -> reply("{\"pros\":[\"전투\"],\"cons\":[\"짧음\"],\"verdict\":\"추천\"}"));
        chatModel.script.add(() -> { throw new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded"); });
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("추천"));
        assertThat(summaries.findByGameId(game.getId())).isPresent();
    }

    @Test
    void serverError503_isRetried_thenSucceeds() throws Exception {
        chatModel.always(() -> reply("{\"pros\":[\"전투\"],\"cons\":[\"짧음\"],\"verdict\":\"추천\"}"));
        chatModel.script.add(() -> { throw new ServerException(503, "UNAVAILABLE", "overloaded"); });
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isOk());
        assertThat(chatModel.calls.get()).isEqualTo(2);
    }

    @Test
    void emptyProsOrCons_returns503_andStoresNothing() throws Exception {
        chatModel.always(() -> reply("{\"pros\":[],\"cons\":[\" \"],\"verdict\":\"추천\"}"));
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable());
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }

    @Test
    void hangingLlm_timesOut_returns503() throws Exception {
        chatModel.always(() -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return reply("{}");
        });
        long start = System.nanoTime();
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable());
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }

    @Test
    void emptyReviewList_returns503OrUsesSeed() throws Exception {
        when(reviews.fetch(anyLong())).thenReturn(List.of());
        mvc.perform(get("/api/games/{id}/summary", game.getId()).session(session))
                .andExpect(status().isServiceUnavailable());
        assertThat(chatModel.calls.get()).isZero();
        assertThat(summaries.findByGameId(game.getId())).isEmpty();
    }
}
