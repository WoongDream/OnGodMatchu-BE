package com.ongodmatchu.domain.auth.validation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HibpRestClientConfig {

  private static final String BASE_URL = "https://api.pwnedpasswords.com";
  private static final int CONNECT_TIMEOUT_MS = 2000;
  private static final int READ_TIMEOUT_MS = 2000;

  @Bean
  public RestClient hibpRestClient(RestClient.Builder builder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return builder.baseUrl(BASE_URL).requestFactory(factory).build();
  }
}
