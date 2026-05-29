package com.ongodmatchu.domain.notice.document;

import java.time.LocalDate;

public record NoticeDocument(String slug, String title, String content, LocalDate publishedAt) {}
