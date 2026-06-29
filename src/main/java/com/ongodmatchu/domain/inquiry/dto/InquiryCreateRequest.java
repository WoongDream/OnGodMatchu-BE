package com.ongodmatchu.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 문의 접수 입력. 제목 최대 50자 / 내용 최대 1000자 (FE 카운터와 정합). */
public record InquiryCreateRequest(
    @NotBlank @Size(max = 50) String title, @NotBlank @Size(max = 1000) String content) {}
