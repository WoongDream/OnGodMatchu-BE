package com.ongodmatchu.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageTransformPolicyTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("toStoredJson_null입력_null반환")
  void toStoredJson_null_returnsNull() {
    assertThat(ImageTransformPolicy.toStoredJson(null)).isNull();
  }

  @Test
  @DisplayName("toStoredJson_JSON_null노드_null반환")
  void toStoredJson_nullNode_returnsNull() {
    JsonNode nullNode = readTree("null");

    assertThat(ImageTransformPolicy.toStoredJson(nullNode)).isNull();
  }

  @Test
  @DisplayName("toStoredJson_정상object_node_toString반환")
  void toStoredJson_validObject_returnsNodeToString() {
    JsonNode node = readTree("{\"x\":1,\"y\":2,\"scale\":1.5}");

    String result = ImageTransformPolicy.toStoredJson(node);

    assertThat(result).isEqualTo(node.toString());
  }

  @Test
  @DisplayName("toStoredJson_2048bytes초과_INVALID_INPUT예외")
  void toStoredJson_exceedsMaxBytes_throwsInvalidInput() {
    StringBuilder sb = new StringBuilder("{\"v\":\"");
    sb.append("a".repeat(3000));
    sb.append("\"}");
    JsonNode node = readTree(sb.toString());

    assertThatThrownBy(() -> ImageTransformPolicy.toStoredJson(node))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }
}
