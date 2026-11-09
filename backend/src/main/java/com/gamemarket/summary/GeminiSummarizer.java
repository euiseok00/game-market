package com.gamemarket.summary;

import com.google.genai.errors.ApiException;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Gemini via Spring AI; response-mime-type=application/json is set in application.yml. */
@Component
public class GeminiSummarizer implements Summarizer {

    private static final int MAX_REVIEWS = 20;
    private static final int MAX_REVIEW_CHARS = 800;

    private static final String SYSTEM = """
            너는 게임 스토어의 리뷰 요약가다. 사용자 리뷰만 근거로 한국어로 요약한다.
            반드시 다음 JSON 하나만 출력한다: {"pros": [문자열 2~4개], "cons": [문자열 1~3개], "verdict": "한두 문장 총평"}
            각 항목은 짧은 한 문장으로 쓴다. 리뷰에 없는 내용은 지어내지 않는다.""";

    private static final int MAX_RETRIES = 3;

    private final ChatClient chat;
    private final JsonMapper json;
    private final long initialBackoffMs;

    public GeminiSummarizer(ChatClient.Builder builder, JsonMapper json,
            @Value("${summary.retry.initial-backoff-ms:1000}") long initialBackoffMs) {
        this.chat = builder.build();
        this.json = json;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public SummaryResult summarize(String gameName, List<String> reviews) {
        StringBuilder user = new StringBuilder("게임: ").append(gameName).append("\n리뷰:\n");
        reviews.stream().limit(MAX_REVIEWS).forEach(r -> user.append("- ")
                .append(r.length() > MAX_REVIEW_CHARS ? r.substring(0, MAX_REVIEW_CHARS) : r)
                .append('\n'));

        return parse(callWithRetry(user.toString()));
    }

    /** 429 gets exponential backoff, up to MAX_RETRIES retries; any other LLM error fails at once. */
    private String callWithRetry(String userText) {
        for (int attempt = 0; ; attempt++) {
            try {
                return chat.prompt().system(SYSTEM).user(userText).call().content();
            } catch (RuntimeException e) {
                if (!isRateLimit(e)) {
                    throw new SummaryUnavailableException("LLM call failed", e);
                }
                if (attempt == MAX_RETRIES) {
                    throw new SummaryUnavailableException("LLM rate limited (429) after retries", e);
                }
                sleep(initialBackoffMs << attempt);
            }
        }
    }

    static boolean isRateLimit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException api && api.code() == 429) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SummaryUnavailableException("interrupted while backing off", ie);
        }
    }

    SummaryResult parse(String content) {
        if (content == null || content.isBlank()) {
            throw new SummaryUnavailableException("empty LLM response");
        }
        SummaryResult result;
        try {
            result = json.readValue(content, SummaryResult.class);
        } catch (JacksonException e) {
            throw new SummaryUnavailableException("malformed LLM JSON", e);
        }
        if (result.pros() == null || result.cons() == null || result.verdict() == null
                || result.verdict().isBlank()) {
            throw new SummaryUnavailableException("incomplete LLM JSON");
        }
        return result;
    }
}
