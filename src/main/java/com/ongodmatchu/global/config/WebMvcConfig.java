package com.ongodmatchu.global.config;

import com.ongodmatchu.global.interceptor.TermsAgreementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final TermsAgreementInterceptor termsAgreementInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(termsAgreementInterceptor).addPathPatterns("/api/**");
  }
}
