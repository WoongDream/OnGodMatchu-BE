package com.ongodmatchu.domain.visit.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.visit.dto.DailyVisitorResponse;
import com.ongodmatchu.domain.visit.dto.DauResponse;
import com.ongodmatchu.domain.visit.dto.VisitorSummaryResponse;
import com.ongodmatchu.domain.visit.service.VisitLogService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatsControllerTest {

  @Autowired private MockMvc mockMvc;

  @SuppressWarnings("unused")
  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean private VisitLogService visitLogService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  // ============ GET /api/stats/dau ============

  @Test
  @DisplayName("getDau_date_미지정_service_getDau_null_호출")
  void getDau_noDateParam_callsServiceWithNull() throws Exception {
    DauResponse response = new DauResponse(LocalDate.of(2026, 5, 28), 123L);
    given(visitLogService.getDau(isNull())).willReturn(response);

    mockMvc
        .perform(get("/api/stats/dau"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-05-28"))
        .andExpect(jsonPath("$.data.visitorCount").value(123));

    then(visitLogService).should().getDau(isNull());
  }

  @Test
  @DisplayName("getDau_date_쿼리파라미터_LocalDate_파싱되어_전달")
  void getDau_withDateParam_parsedAndForwarded() throws Exception {
    LocalDate target = LocalDate.of(2026, 5, 28);
    DauResponse response = new DauResponse(target, 7L);
    given(visitLogService.getDau(eq(target))).willReturn(response);

    mockMvc
        .perform(get("/api/stats/dau").param("date", "2026-05-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-05-28"))
        .andExpect(jsonPath("$.data.visitorCount").value(7));

    then(visitLogService).should().getDau(eq(target));
  }

  @Test
  @DisplayName("getDau_visitorCount_0_정상_직렬화")
  void getDau_zeroVisitorCount_serializedCorrectly() throws Exception {
    LocalDate target = LocalDate.of(2026, 5, 28);
    DauResponse response = new DauResponse(target, 0L);
    given(visitLogService.getDau(eq(target))).willReturn(response);

    mockMvc
        .perform(get("/api/stats/dau").param("date", "2026-05-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-05-28"))
        .andExpect(jsonPath("$.data.visitorCount").value(0));
  }

  @Test
  @DisplayName("getDau_DauResponse_date_visitorCount_JSON필드_노출")
  void getDau_dauResponseFieldsExposed() throws Exception {
    LocalDate target = LocalDate.of(2026, 1, 15);
    DauResponse response = new DauResponse(target, 9999L);
    given(visitLogService.getDau(eq(target))).willReturn(response);

    mockMvc
        .perform(get("/api/stats/dau").param("date", "2026-01-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-01-15"))
        .andExpect(jsonPath("$.data.visitorCount").value(9999));

    then(visitLogService).should().getDau(eq(target));
  }

  // ============ GET /api/stats/visitors ============

  @Test
  @DisplayName("getVisitorSummary_today_total_daily7개_JSON필드_노출")
  void getVisitorSummary_serializesAllFields() throws Exception {
    LocalDate today = LocalDate.of(2026, 5, 28);
    List<DailyVisitorResponse> daily =
        List.of(
            new DailyVisitorResponse(today.minusDays(6), 0L),
            new DailyVisitorResponse(today.minusDays(5), 0L),
            new DailyVisitorResponse(today.minusDays(4), 0L),
            new DailyVisitorResponse(today.minusDays(3), 0L),
            new DailyVisitorResponse(today.minusDays(2), 0L),
            new DailyVisitorResponse(today.minusDays(1), 0L),
            new DailyVisitorResponse(today, 63L));
    VisitorSummaryResponse response = new VisitorSummaryResponse(63L, 7_600_000L, daily);
    given(visitLogService.getVisitorSummary()).willReturn(response);

    mockMvc
        .perform(get("/api/stats/visitors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.today").value(63))
        .andExpect(jsonPath("$.data.total").value(7600000))
        .andExpect(jsonPath("$.data.daily.length()").value(7))
        .andExpect(jsonPath("$.data.daily[0].date").exists())
        .andExpect(jsonPath("$.data.daily[0].visitorCount").exists())
        .andExpect(jsonPath("$.data.daily[6].visitorCount").value(63));
  }

  @Test
  @DisplayName("getVisitorSummary_service_getVisitorSummary_호출검증")
  void getVisitorSummary_callsService() throws Exception {
    LocalDate today = LocalDate.of(2026, 5, 28);
    List<DailyVisitorResponse> daily =
        List.of(
            new DailyVisitorResponse(today.minusDays(6), 0L),
            new DailyVisitorResponse(today.minusDays(5), 0L),
            new DailyVisitorResponse(today.minusDays(4), 0L),
            new DailyVisitorResponse(today.minusDays(3), 0L),
            new DailyVisitorResponse(today.minusDays(2), 0L),
            new DailyVisitorResponse(today.minusDays(1), 0L),
            new DailyVisitorResponse(today, 63L));
    VisitorSummaryResponse response = new VisitorSummaryResponse(63L, 7_600_000L, daily);
    given(visitLogService.getVisitorSummary()).willReturn(response);

    mockMvc.perform(get("/api/stats/visitors")).andExpect(status().isOk());

    then(visitLogService).should().getVisitorSummary();
  }
}
