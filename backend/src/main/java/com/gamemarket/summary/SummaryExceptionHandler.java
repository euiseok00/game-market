package com.gamemarket.summary;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SummaryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SummaryExceptionHandler.class);

    @ExceptionHandler(SummaryUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(SummaryUnavailableException e) {
        log.warn("summary unavailable: {}", e.getMessage(), e.getCause());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "SUMMARY_UNAVAILABLE",
                "message", "AI 요약을 지금 만들 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }
}
