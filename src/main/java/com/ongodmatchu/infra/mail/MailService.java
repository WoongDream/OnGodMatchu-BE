package com.ongodmatchu.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String fromAddress;

  public void sendVerificationCode(String to, String code) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(to);
      message.setSubject("[OnGodMatchu] 이메일 인증 코드");
      message.setText("인증 코드: " + code + "\n\n5분 이내에 입력해주세요.");
      mailSender.send(message);
    } catch (Exception e) {
      log.warn("[MailService] 메일 발송 실패 — 로컬 확인용 코드: {} (to: {}), 원인: {}", code, to, e.getMessage());
    }
  }
}
