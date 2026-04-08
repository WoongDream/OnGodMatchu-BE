package com.ongodmatchu.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class AiGradingService {

  private static final String API_URL = "https://api.anthropic.com/v1/messages";
  private static final String MODEL = "claude-haiku-4-5-20251001";
  private static final int MAX_TOKENS = 10;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Value("${ai.claude.api-key:}")
  private String apiKey;

  public AiGradingService(ObjectMapper objectMapper) {
    this.restClient = RestClient.create();
    this.objectMapper = objectMapper;
  }

  /**
   * 주관식 정답을 채점한다.
   *
   * <p>exact match 후에도 판단이 필요한 경우에만 호출되므로 비용은 최소화된다.
   */
  public boolean grade(String correctAnswer, String userAnswer) {
    if (!isApiKeyConfigured()) {
      log.warn("[AiGradingService] API 키 미설정 — false 반환");
      return false;
    }

    try {
      String prompt = buildPrompt(correctAnswer, userAnswer);
      String requestBody = buildRequestBody(prompt);

      String response =
          restClient
              .post()
              .uri(API_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-api-key", apiKey)
              .header("anthropic-version", "2023-06-01")
              .body(requestBody)
              .retrieve()
              .body(String.class);

      return parseResult(response);
    } catch (Exception e) {
      log.error(
          "[AiGradingService] 채점 중 오류 발생: {} / {}", e.getClass().getSimpleName(), e.getMessage());
      return false;
    }
  }

  private String buildPrompt(String correctAnswer, String userAnswer) {
    return String.format(
        "퀴즈 채점. 정답: \"%s\" / 사용자 답변: \"%s\"\n핵심 단어가 일치하거나 의미가 같으면 true, 다르면 false. 단어 하나만 출력.",
        correctAnswer, userAnswer);
  }

  private String buildRequestBody(String prompt) {
    return String.format(
        "{\"model\":\"%s\",\"max_tokens\":%d,\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
        MODEL, MAX_TOKENS, escapeJson(prompt));
  }

  private boolean parseResult(String responseBody) throws Exception {
    JsonNode root = objectMapper.readTree(responseBody);
    String text = root.path("content").get(0).path("text").asText().trim().toLowerCase();
    log.info("[AiGradingService] 응답: {}", text);
    return text.startsWith("true");
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private boolean isApiKeyConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
