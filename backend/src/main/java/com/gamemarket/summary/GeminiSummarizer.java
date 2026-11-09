package com.gamemarket.summary;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
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

    private final ChatClient chat;
    private final JsonMapper json;

    public GeminiSummarizer(ChatClient.Builder builder, JsonMapper json) {
        this.chat = builder.build();
        this.json = json;
    }

    @Override
    public SummaryResult summarize(String gameName, List<String> reviews) {
        StringBuilder user = new StringBuilder("게임: ").append(gameName).append("\n리뷰:\n");
        reviews.stream().limit(MAX_REVIEWS).forEach(r -> user.append("- ")
                .append(r.length() > MAX_REVIEW_CHARS ? r.substring(0, MAX_REVIEW_CHARS) : r)
                .append('\n'));

        String content = chat.prompt().system(SYSTEM).user(user.toString()).call().content();
        return parse(content);
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
