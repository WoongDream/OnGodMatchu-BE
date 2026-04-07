package com.ongodmatchu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class OngodmatchuApplication {

  public static void main(String[] args) {
    SpringApplication.run(OngodmatchuApplication.class, args);
  }
}
