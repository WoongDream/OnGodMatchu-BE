package com.ongodmatchu.global.config;

import com.ongodmatchu.domain.auth.jwt.JwtAuthenticationFilter;
import com.ongodmatchu.domain.auth.jwt.JwtProvider;
import com.ongodmatchu.domain.auth.oauth2.CustomOAuth2UserService;
import com.ongodmatchu.domain.auth.oauth2.OAuth2AuthenticationFailureHandler;
import com.ongodmatchu.domain.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import com.ongodmatchu.domain.auth.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtProvider jwtProvider;
  private final CustomUserDetailsService userDetailsService;
  private final CustomOAuth2UserService oAuth2UserService;
  private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
  private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;
  private final CorsConfigurationSource corsConfigurationSource;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/**",
                        "/api/health",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/quizzes/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/upload/signed")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/users/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/users/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/quizzes/*/play", "/api/quizzes/grade")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(ep -> ep.userService(oAuth2UserService))
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider, userDetailsService),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
