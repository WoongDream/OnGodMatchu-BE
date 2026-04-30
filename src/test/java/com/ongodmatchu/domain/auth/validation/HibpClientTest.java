package com.ongodmatchu.domain.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HibpClientTest {

  // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
  private static final String PASSWORD_SHA1_PREFIX = "5BAA6";
  private static final String PASSWORD_SHA1_SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

  private static final String BASE_URL = "https://api.pwnedpasswords.com";

  private HibpClient hibpClient;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    mockServer = MockRestServiceServer.bindTo(builder).build();
    hibpClient = new HibpClient(builder.build());
  }

  // ============ 유출된 비밀번호 감지 ============

  @Test
  @DisplayName("알려진유출비밀번호_suffix존재_true반환")
  void isBreached_knownBreachedPassword_returnsTrue() {
    String responseBody =
        PASSWORD_SHA1_SUFFIX + ":12345\r\nDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEA:1\r\n";

    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Add-Padding", "true"))
        .andRespond(withSuccess(responseBody, MediaType.TEXT_PLAIN));

    boolean result = hibpClient.isBreached("password");

    assertThat(result).isTrue();
    mockServer.verify();
  }

  @Test
  @DisplayName("suffix미존재_false반환")
  void isBreached_suffixNotPresent_returnsFalse() {
    String responseBody =
        "AAAAABBBBBCCCCCDDDDDEEEEEFFFFFF00000:99\r\nBBBBBCCCCCDDDDDEEEEEFFFFF0000011111:5\r\n";

    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.TEXT_PLAIN));

    boolean result = hibpClient.isBreached("password");

    assertThat(result).isFalse();
    mockServer.verify();
  }

  @Test
  @DisplayName("응답_빈본문_false반환")
  void isBreached_emptyBody_returnsFalse() {
    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

    boolean result = hibpClient.isBreached("password");

    assertThat(result).isFalse();
    mockServer.verify();
  }

  @Test
  @DisplayName("서버500오류_페일오픈_false반환")
  void isBreached_serverError_returnsFalseFailOpen() {
    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    boolean result = hibpClient.isBreached("password");

    assertThat(result).isFalse();
    mockServer.verify();
  }

  @Test
  @DisplayName("요청경로_prefix5자_Add-Padding헤더포함")
  void isBreached_requestShape_correctEndpointAndHeader() {
    String responseBody = "AAAAABBBBBCCCCCDDDDDEEEEEFFFFFF00000:1\r\n";

    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Add-Padding", "true"))
        .andRespond(withSuccess(responseBody, MediaType.TEXT_PLAIN));

    hibpClient.isBreached("password");

    mockServer.verify();
  }

  @Test
  @DisplayName("suffix_소문자응답_대소문자무시_true반환")
  void isBreached_suffixInLowercase_caseInsensitiveMatch_returnsTrue() {
    // SHA-1 해시는 대문자로 생성되지만, 서버 응답이 소문자 suffix를 반환하더라도 매칭되어야 한다.
    String lowercaseSuffix = PASSWORD_SHA1_SUFFIX.toLowerCase();
    String responseBody = lowercaseSuffix + ":1\r\n";

    mockServer
        .expect(requestTo(BASE_URL + "/range/" + PASSWORD_SHA1_PREFIX))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.TEXT_PLAIN));

    boolean result = hibpClient.isBreached("password");

    assertThat(result).isTrue();
    mockServer.verify();
  }

  @Test
  @DisplayName("유출되지않은비밀번호_false반환")
  void isBreached_safePassword_returnsFalse() {
    // "safePassword123" — 실제 SHA-1 계산 후 prefix 추출 (테스트는 서버 응답으로 제어)
    // 이 테스트는 매칭 로직이 false를 올바르게 반환함을 검증한다.
    mockServer
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/range/")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess("AAAAABBBBBCCCCCDDDDDEEEEEFFFFFF00000:1\r\n", MediaType.TEXT_PLAIN));

    boolean result = hibpClient.isBreached("safePassword123");

    assertThat(result).isFalse();
    mockServer.verify();
  }
}
