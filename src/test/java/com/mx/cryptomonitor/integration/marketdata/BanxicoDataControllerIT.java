package com.mx.cryptomonitor.integration.marketdata;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.marketdata.application.dto.response.CetesRateTableEntry;
import com.mx.cryptomonitor.marketdata.application.service.BanxicoCurveService;
import com.mx.cryptomonitor.marketdata.application.service.CetesRateTableService;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BanxicoDataControllerIT {

  @Autowired private MockMvc mockMvc;

  @MockBean private BanxicoCurveService banxicoCurveService;
  @MockBean private CetesRateTableService cetesRateTableService;

  @Test
  void getCetesCurveReturnsCurveFromService() throws Exception {
    when(banxicoCurveService.getCurve())
        .thenReturn(Map.of(28, new BigDecimal("6.18"), 91, new BigDecimal("6.49")));

    mockMvc
        .perform(get("/api/v1/marketdata/banxico/cetes/curve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.28").value(6.18))
        .andExpect(jsonPath("$.91").value(6.49));
  }

  @Test
  void getCetesTableReturnsRowsFromService() throws Exception {
    CetesRateTableEntry entry =
        new CetesRateTableEntry(
            28,
            "1 mes",
            new BigDecimal("9.95"),
            new BigDecimal("6.1800"),
            LocalDate.of(2026, 7, 23));
    when(cetesRateTableService.getCetesTable()).thenReturn(List.of(entry));

    mockMvc
        .perform(get("/api/v1/marketdata/banxico/cetes/table"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].plazoDias").value(28))
        .andExpect(jsonPath("$[0].plazoLabel").value("1 mes"))
        .andExpect(jsonPath("$[0].precio").value(9.95))
        .andExpect(jsonPath("$[0].tasa").value(6.18))
        .andExpect(jsonPath("$[0].fecha").value("2026-07-23"));
  }
}
