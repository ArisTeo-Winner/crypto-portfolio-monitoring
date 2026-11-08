package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort.AssetDisplay;
import com.mx.cryptomonitor.transaction.application.dto.response.UserDividendResponse;
import com.mx.cryptomonitor.transaction.application.service.UserDividendQueryService;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.DividendType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;

@ExtendWith(MockitoExtension.class)
class UserDividendQueryServiceTest {

  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;

  private UserDividendQueryService service;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new UserDividendQueryService(dividendDetailRepository, assetCatalogQueryPort);
  }

  private DividendDetail dividend(BigDecimal gross, BigDecimal tax, BigDecimal amountPerShare) {
    Transaction tx = new Transaction();
    tx.setAssetSymbol("GOOGL");
    tx.setTransactionType("DIVIDEND");
    tx.setTotalValue(gross);
    tx.setCurrency("USD");
    tx.setTransactionDate(OffsetDateTime.of(2026, 6, 15, 4, 0, 0, 0, ZoneOffset.UTC));
    return DividendDetail.builder()
        .transaction(tx)
        .exDividendDate(LocalDate.of(2026, 6, 16))
        .dividendType(DividendType.CASH)
        .taxWithheld(tax)
        .amountPerShare(amountPerShare)
        .build();
  }

  @Test
  void mapsReceivedDividendWithSharesNetAndDisplay() {
    when(dividendDetailRepository.findUserDividends(eq(userId), any()))
        .thenReturn(
            List.of(
                dividend(new BigDecimal("0.40"), new BigDecimal("0.06"), new BigDecimal("0.20"))));
    when(assetCatalogQueryPort.findDisplayBySymbols(any()))
        .thenReturn(Map.of("GOOGL", new AssetDisplay("Alphabet Inc. Class A", "logo.png")));

    List<UserDividendResponse> result = service.getUserDividends(userId);

    assertThat(result).hasSize(1);
    UserDividendResponse r = result.get(0);
    assertThat(r.symbol()).isEqualTo("GOOGL");
    assertThat(r.assetName()).isEqualTo("Alphabet Inc. Class A");
    assertThat(r.logoUrl()).isEqualTo("logo.png");
    assertThat(r.type()).isEqualTo("CASH");
    assertThat(r.currency()).isEqualTo("USD");
    assertThat(r.grossAmount()).isEqualByComparingTo("0.40");
    assertThat(r.taxWithheld()).isEqualByComparingTo("0.06");
    assertThat(r.netAmount()).isEqualByComparingTo("0.34"); // 0.40 - 0.06
    assertThat(r.amountPerShare()).isEqualByComparingTo("0.20");
    assertThat(r.shares()).isEqualByComparingTo("2"); // 0.40 / 0.20
  }

  @Test
  void sharesNullWhenAmountPerShareMissing() {
    when(dividendDetailRepository.findUserDividends(eq(userId), any()))
        .thenReturn(List.of(dividend(new BigDecimal("0.40"), BigDecimal.ZERO, null)));
    when(assetCatalogQueryPort.findDisplayBySymbols(any())).thenReturn(Map.of());

    List<UserDividendResponse> result = service.getUserDividends(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).shares()).isNull();
    assertThat(result.get(0).amountPerShare()).isNull();
    assertThat(result.get(0).netAmount()).isEqualByComparingTo("0.40");
    assertThat(result.get(0).assetName()).isNull();
  }

  @Test
  void emptyWhenNoDividends() {
    when(dividendDetailRepository.findUserDividends(eq(userId), any())).thenReturn(List.of());

    assertThat(service.getUserDividends(userId)).isEmpty();
  }
}
