package com.mx.cryptomonitor.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.shared.dto.request.PortfolioEntryRequest;
import com.mx.cryptomonitor.shared.dto.request.RefreshTokenRequest;
import com.mx.cryptomonitor.shared.dto.response.CmcQuotesLatestResponse;
import com.mx.cryptomonitor.shared.dto.response.ErrorResponseDTO;
import com.mx.cryptomonitor.shared.dto.response.HistoricalPriceResonseDTO;
import com.mx.cryptomonitor.shared.dto.response.PortfolioEntryResponse;
import com.mx.cryptomonitor.shared.errors.MarketDataErrorCategory;

class SharedDtoSmokeTest {

  @Test
  void shouldInstantiateSharedDtosAndEnum() {
    CmcQuote quote = new CmcQuote(new BigDecimal("12.34"));
    CmcCrypto crypto = new CmcCrypto("BTC", Map.of("USD", quote));
    CmcStatus status = new CmcStatus(Instant.parse("2026-03-15T10:15:30Z"), 0, null, "10", "1");
    RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest("refresh-token");
    CmcQuotesLatestResponse latestResponse =
        new CmcQuotesLatestResponse(status, Map.of("BTC", List.of(crypto)));
    ErrorResponseDTO errorResponse =
        new ErrorResponseDTO(
            LocalDateTime.of(2026, 3, 15, 5, 30), 400, "Bad Request", List.of("invalid"), "/api");
    HistoricalPriceResonseDTO historical =
        new HistoricalPriceResonseDTO("AAPL", LocalDate.of(2026, 3, 15), new BigDecimal("172.35"));
    PortfolioEntryResponse portfolioEntryResponse =
        new PortfolioEntryResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC",
            "CRYPTO",
            BigDecimal.ONE,
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            new BigDecimal("12.00"),
            new BigDecimal("2.00"),
            LocalDateTime.of(2026, 3, 15, 5, 30),
            LocalDateTime.of(2026, 3, 15, 5, 0),
            LocalDateTime.of(2026, 3, 15, 5, 45));

    assertThat(quote.price()).isEqualByComparingTo("12.34");
    assertThat(crypto.symbol()).isEqualTo("BTC");
    assertThat(status.error_code()).isZero();
    assertThat(refreshTokenRequest.refreshToken()).isEqualTo("refresh-token");
    assertThat(latestResponse.data()).containsKey("BTC");
    assertThat(errorResponse.error()).isEqualTo("Bad Request");
    assertThat(historical.symbol()).isEqualTo("AAPL");
    assertThat(portfolioEntryResponse.assetSymbol()).isEqualTo("BTC");
    assertThat(new PortfolioEntryRequest()).isNotNull();
    assertThat(MarketDataErrorCategory.valueOf("RATE_LIMIT"))
        .isEqualTo(MarketDataErrorCategory.RATE_LIMIT);
    assertThat(MarketDataErrorCategory.values()).hasSizeGreaterThan(1);
  }
}
