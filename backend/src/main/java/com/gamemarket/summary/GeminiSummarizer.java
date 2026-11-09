package com.gamemarket.summary;

import com.google.genai.errors.ApiException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final long timeoutMs;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    public GeminiSummarizer(ChatClient.Builder builder, JsonMapper json,
            @Value("${summary.retry.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${summary.llm-timeout-ms:20000}") long timeoutMs) {
        this.chat = builder.build();
        this.json = json;
        this.initialBackoffMs = initialBackoffMs;
        this.timeoutMs = timeoutMs;
    }

    @PreDestroy
    void shutdown() {
        calls.shutdownNow();
    }

    @Override
    public SummaryResult summarize(String gameName, List<String> reviews) {
        StringBuilder user = new StringBuilder("게임: ").append(gameName).append("\n리뷰:\n");
        reviews.stream().limit(MAX_REVIEWS).forEach(r -> user.append("- ")
                .append(r.length() > MAX_REVIEW_CHARS ? r.substring(0, MAX_REVIEW_CHARS) : r)
                .append('\n'));

        return parse(callWithRetry(user.toString()));
    }

    /**
     * 429 and 5xx get exponential backoff, up to MAX_RETRIES retries; other errors and timeouts fail at once
     * (a timed-out call may still be running server side, so retrying would only pile up load).
     */
    private String callWithRetry(String userText) {
        for (int attempt = 0; ; attempt++) {
            try {
                return callWithTimeout(userText);
            } catch (RuntimeException e) {
                if (!isRetryable(e)) {
                    throw e instanceof SummaryUnavailableException s ? s
                            : new SummaryUnavailableException("LLM call failed", e);
                }
                if (attempt == MAX_RETRIES) {
                    throw new SummaryUnavailableException("LLM unavailable after retries", e);
                }
                sleep(initialBackoffMs << attempt);
            }
        }
    }

    private String callWithTimeout(String userText) {
        Future<String> call = calls.submit(() -> chat.prompt().system(SYSTEM).user(userText).call().content());
        try {
            return call.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new SummaryUnavailableException("LLM call timed out after " + timeoutMs + " ms", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new SummaryUnavailableException("interrupted while waiting for LLM", e);
        }
    }

    static boolean isRetryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException api && (api.code() == 429 || api.code() >= 500)) {
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
        if (blankList(result.pros()) || blankList(result.cons()) || result.verdict() == null
                || result.verdict().isBlank()) {
            throw new SummaryUnavailableException("incomplete LLM JSON");
        }
        return result;
    }

    private static boolean blankList(List<String> items) {
        return items == null || items.isEmpty() || items.stream().anyMatch(i -> i == null || i.isBlank());
    }
}
