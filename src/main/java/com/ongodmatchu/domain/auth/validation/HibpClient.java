package com.ongodmatchu.domain.auth.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class HibpClient {

  private final RestClient restClient;

  public HibpClient(@Qualifier("hibpRestClient") RestClient hibpRestClient) {
    this.restClient = hibpRestClient;
  }

  public boolean isBreached(String rawPassword) {
    String sha1 = sha1Hex(rawPassword);
    String prefix = sha1.substring(0, 5);
    String suffix = sha1.substring(5);

    try {
      String body =
          restClient
              .get()
              .uri("/range/{prefix}", prefix)
              .header("Add-Padding", "true")
              .retrieve()
              .body(String.class);
      if (body == null || body.isBlank()) {
        return false;
      }
      return body.lines()
          .map(line -> line.split(":", 2)[0].trim())
          .anyMatch(suffix::equalsIgnoreCase);
    } catch (RestClientException e) {
      log.warn("HIBP lookup failed — allowing signup. error={}", e.getMessage());
      return false;
    }
  }

  private static String sha1Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02X", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 algorithm not available", e);
    }
  }
}
