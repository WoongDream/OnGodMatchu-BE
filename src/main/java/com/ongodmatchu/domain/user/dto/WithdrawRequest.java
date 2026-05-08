package com.ongodmatchu.domain.user.dto;

/** 회원탈퇴 요청. LOCAL 계정은 currentPassword 필수, OAuth 계정은 생략 가능. */
public record WithdrawRequest(String currentPassword) {}
