package com.ongodmatchu.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final JavaMailSender mailSender;

  public void sendVerificationCode(String to, String code) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(to);
      message.setSubject("[OnGodMatchu] 이메일 인증 코드");
      message.setText("인증 코드: " + code + "\n\n5분 이내에 입력해주세요.");
      mailSender.send(message);
    } catch (Exception e) {
      // 로컬 환경에서 SMTP 미설정 시 콘솔로 확인
      log.warn("[MailService] 메일 발송 실패 — 로컬 확인용 코드: {} (to: {})", code, to);
    }
  }
}
