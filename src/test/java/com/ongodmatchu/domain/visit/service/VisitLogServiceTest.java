package com.ongodmatchu.domain.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.visit.dto.DailyVisitorResponse;
import com.ongodmatchu.domain.visit.dto.DauResponse;
import com.ongodmatchu.domain.visit.dto.VisitorSummaryResponse;
import com.ongodmatchu.domain.visit.entity.VisitLog;
import com.ongodmatchu.domain.visit.repository.VisitLogRepository;
import com.ongodmatchu.domain.visit.repository.VisitLogRepository.DailyVisitorRow;
import com.ongodmatchu.global.util.TimeFormat;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VisitLogServiceTest {

  @InjectMocks private VisitLogService visitLogService;
  @Mock private VisitLogRepository visitLogRepository;
  @Mock private UserRepository userRepository;

  private User testUser(Long id) {
    User user =
        User.builder()
            .email("u" + id + "@example.com")
            .nickname("user" + id)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  @Nested
  @DisplayName("recordVisit")
  class RecordVisit {

    @Test
    @DisplayName("anonId+userId+path정상_user조회후save호출")
    void allValid_savesWithUser() {
      String anonId = UUID.randomUUID().toString();
      User user = testUser(1L);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      visitLogService.recordVisit(anonId, 1L, "/quiz/10");

      ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
      then(visitLogRepository).should().save(captor.capture());
      VisitLog saved = captor.getValue();
      assertThat(saved.getAnonId()).isEqualTo(anonId);
      assertThat(saved.getUser()).isSameAs(user);
      assertThat(saved.getPath()).isEqualTo("/quiz/10");
    }

    @Test
    @DisplayName("userId가null_user없이save호출")
    void noUserId_savesWithoutUser() {
      String anonId = UUID.randomUUID().toString();

      visitLogService.recordVisit(anonId, null, "/");

      ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
      then(visitLogRepository).should().save(captor.capture());
      VisitLog saved = captor.getValue();
      assertThat(saved.getAnonId()).isEqualTo(anonId);
      assertThat(saved.getUser()).isNull();
      assertThat(saved.getPath()).isEqualTo("/");
      then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("anonId가null_save호출안됨")
    void nullAnonId_skips() {
      visitLogService.recordVisit(null, 1L, "/");

      then(visitLogRepository).should(never()).save(any());
      then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("anonId가blank_save호출안됨")
    void blankAnonId_skips() {
      visitLogService.recordVisit("   ", 1L, "/");

      then(visitLogRepository).should(never()).save(any());
      then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("path가null_save호출안됨")
    void nullPath_skips() {
      visitLogService.recordVisit(UUID.randomUUID().toString(), 1L, null);

      then(visitLogRepository).should(never()).save(any());
      then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("path가blank_save호출안됨")
    void blankPath_skips() {
      visitLogService.recordVisit(UUID.randomUUID().toString(), 1L, "  ");

      then(visitLogRepository).should(never()).save(any());
      then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("path가256자_255자로truncate후save")
    void pathTooLong_truncatedTo255() {
      String anonId = UUID.randomUUID().toString();
      String longPath = "/" + "a".repeat(255); // 길이 256
      assertThat(longPath).hasSize(256);

      visitLogService.recordVisit(anonId, null, longPath);

      ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
      then(visitLogRepository).should().save(captor.capture());
      VisitLog saved = captor.getValue();
      assertThat(saved.getPath()).hasSize(255);
      assertThat(saved.getPath()).isEqualTo(longPath.substring(0, 255));
    }

    @Test
    @DisplayName("userId존재하나userRepository가empty_user=null로save")
    void userIdMissingInRepo_savesWithNullUser() {
      String anonId = UUID.randomUUID().toString();
      given(userRepository.findById(99L)).willReturn(Optional.empty());

      visitLogService.recordVisit(anonId, 99L, "/x");

      ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
      then(visitLogRepository).should().save(captor.capture());
      assertThat(captor.getValue().getUser()).isNull();
    }

    @Test
    @DisplayName("save가예외던져도swallow_정상리턴")
    void saveThrows_swallowed() {
      String anonId = UUID.randomUUID().toString();
      willThrow(new RuntimeException("db down")).given(visitLogRepository).save(any());

      assertThatCode(() -> visitLogService.recordVisit(anonId, null, "/"))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("getDau")
  class GetDau {

    @Test
    @DisplayName("date가null_오늘KST기준range로조회")
    void nullDate_usesTodayKst() {
      LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
      LocalDateTime expectedStart = today.atStartOfDay();
      LocalDateTime expectedEnd = today.plusDays(1).atStartOfDay();
      given(visitLogRepository.countDistinctVisitors(expectedStart, expectedEnd)).willReturn(42L);

      DauResponse response = visitLogService.getDau(null);

      assertThat(response.date()).isEqualTo(today);
      assertThat(response.visitorCount()).isEqualTo(42L);
      then(visitLogRepository).should().countDistinctVisitors(expectedStart, expectedEnd);
    }

    @Test
    @DisplayName("특정날짜지정_해당일range로조회")
    void specificDate_usesGivenRange() {
      LocalDate target = LocalDate.of(2026, 5, 7);
      LocalDateTime expectedStart = target.atStartOfDay();
      LocalDateTime expectedEnd = target.plusDays(1).atStartOfDay();
      given(visitLogRepository.countDistinctVisitors(expectedStart, expectedEnd)).willReturn(7L);

      DauResponse response = visitLogService.getDau(target);

      assertThat(response.date()).isEqualTo(target);
      assertThat(response.visitorCount()).isEqualTo(7L);
      then(visitLogRepository).should().countDistinctVisitors(expectedStart, expectedEnd);
    }

    @Test
    @DisplayName("visitorCount0_DauResponse_0반환")
    void zeroVisitors_returnsZero() {
      LocalDate target = LocalDate.of(2026, 1, 1);
      given(visitLogRepository.countDistinctVisitors(any(), any())).willReturn(0L);

      DauResponse response = visitLogService.getDau(target);

      assertThat(response)
          .extracting(DauResponse::date, DauResponse::visitorCount)
          .containsExactly(target, 0L);
      assertThat(response.visitorCount()).asInstanceOf(InstanceOfAssertFactories.LONG).isZero();
    }
  }

  @Nested
  @DisplayName("getVisitorSummary")
  class GetVisitorSummary {

    private DailyVisitorRow row(LocalDate date, long count) {
      DailyVisitorRow row = mock(DailyVisitorRow.class);
      given(row.getD()).willReturn(Date.valueOf(date));
      given(row.getC()).willReturn(count);
      return row;
    }

    @Test
    @DisplayName("repository_빈리스트_today0_total0_daily7개모두0")
    void emptyResult_returnsZeroes() {
      LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
      given(visitLogRepository.countDistinctVisitorsDaily(any(), any()))
          .willReturn(Collections.emptyList());
      given(visitLogRepository.countTotalVisitors()).willReturn(0L);

      VisitorSummaryResponse response = visitLogService.getVisitorSummary();

      assertThat(response.today()).isZero();
      assertThat(response.total()).isZero();
      assertThat(response.daily()).hasSize(7);
      assertThat(response.daily())
          .extracting(DailyVisitorResponse::visitorCount)
          .containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L);
      assertThat(response.daily())
          .extracting(DailyVisitorResponse::date)
          .containsExactly(
              today.minusDays(6),
              today.minusDays(5),
              today.minusDays(4),
              today.minusDays(3),
              today.minusDays(2),
              today.minusDays(1),
              today);
    }

    @Test
    @DisplayName("오늘row만존재_today=row값_나머지6일0_total은repository값")
    void onlyTodayRow_mapsTodayAndTotal() {
      LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
      List<DailyVisitorRow> rows = List.of(row(today, 63L));
      given(visitLogRepository.countDistinctVisitorsDaily(any(), any())).willReturn(rows);
      given(visitLogRepository.countTotalVisitors()).willReturn(1000L);

      VisitorSummaryResponse response = visitLogService.getVisitorSummary();

      assertThat(response.today()).isEqualTo(63L);
      assertThat(response.total()).isEqualTo(1000L);
      assertThat(response.daily()).hasSize(7);
      assertThat(response.daily().get(6).visitorCount()).isEqualTo(63L);
      assertThat(response.daily().get(6).date()).isEqualTo(today);
      assertThat(response.daily().subList(0, 6))
          .extracting(DailyVisitorResponse::visitorCount)
          .containsExactly(0L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("중간일자만존재_누락일은0으로채워지고daily오름차순")
    void sparseRows_filledWithZeroAndAscending() {
      LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
      LocalDate fiveDaysAgo = today.minusDays(5);
      LocalDate threeDaysAgo = today.minusDays(3);
      List<DailyVisitorRow> rows = List.of(row(fiveDaysAgo, 11L), row(threeDaysAgo, 22L));
      given(visitLogRepository.countDistinctVisitorsDaily(any(), any())).willReturn(rows);
      given(visitLogRepository.countTotalVisitors()).willReturn(500L);

      VisitorSummaryResponse response = visitLogService.getVisitorSummary();

      assertThat(response.daily()).hasSize(7);
      assertThat(response.daily())
          .extracting(DailyVisitorResponse::date)
          .containsExactly(
              today.minusDays(6),
              today.minusDays(5),
              today.minusDays(4),
              today.minusDays(3),
              today.minusDays(2),
              today.minusDays(1),
              today);
      assertThat(response.daily())
          .extracting(DailyVisitorResponse::visitorCount)
          .containsExactly(0L, 11L, 0L, 22L, 0L, 0L, 0L);
      assertThat(response.today()).isZero();
      assertThat(response.total()).isEqualTo(500L);
    }

    @Test
    @DisplayName("오늘row없을때_today=0_total은repository값그대로호출")
    void noTodayRow_todayIsZeroButTotalStillCalled() {
      LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
      List<DailyVisitorRow> rows = List.of(row(today.minusDays(2), 5L));
      given(visitLogRepository.countDistinctVisitorsDaily(any(), any())).willReturn(rows);
      given(visitLogRepository.countTotalVisitors()).willReturn(777L);

      VisitorSummaryResponse response = visitLogService.getVisitorSummary();

      assertThat(response.today()).isZero();
      assertThat(response.total()).isEqualTo(777L);
      then(visitLogRepository).should().countTotalVisitors();
    }

    @Test
    @DisplayName("daily사이즈는항상7")
    void dailySizeAlwaysSeven() {
      given(visitLogRepository.countDistinctVisitorsDaily(any(), any()))
          .willReturn(Collections.emptyList());
      given(visitLogRepository.countTotalVisitors()).willReturn(0L);

      VisitorSummaryResponse response = visitLogService.getVisitorSummary();

      assertThat(response.daily()).hasSize(7);
    }
  }
}
