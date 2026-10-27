package com.mx.cryptomonitor.unit.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.asset.application.port.out.InstrumentIdentityPort;
import com.mx.cryptomonitor.asset.application.port.out.InstrumentIdentityPort.InstrumentIdentity;
import com.mx.cryptomonitor.asset.application.service.InstrumentIdentityService;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

@ExtendWith(MockitoExtension.class)
class InstrumentIdentityServiceTest {

  @Mock private InstrumentIdentityPort instrumentIdentityPort;
  @Mock private AssetCatalogRepository catalogRepository;
  @InjectMocks private InstrumentIdentityService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "defaultExchCode", "US");
    ReflectionTestUtils.setField(service, "rateLimitDelayMs", 0L);
  }

  private static AssetCatalogEntity stock(String symbol) {
    return AssetCatalogEntity.builder().symbol(symbol).assetType("STOCK").build();
  }

  @Test
  void poblaFigiEnFilasPendientes() {
    AssetCatalogEntity meli = stock("MELI");
    when(catalogRepository.findByAssetTypeInAndShareClassFigiIsNull(List.of("STOCK", "ETF")))
        .thenReturn(List.of(meli));
    when(instrumentIdentityPort.resolveByTicker("MELI", "US"))
        .thenReturn(
            Optional.of(
                new InstrumentIdentity(
                    "BBG000GQRXP0", "BBG001S5VPN7", "Common Stock", "Equity", "MERCADOLIBRE INC")));

    int resolved = service.backfillMissingIdentities();

    assertThat(resolved).isEqualTo(1);
    ArgumentCaptor<AssetCatalogEntity> saved = ArgumentCaptor.forClass(AssetCatalogEntity.class);
    verify(catalogRepository).save(saved.capture());
    assertThat(saved.getValue().getCompositeFigi()).isEqualTo("BBG000GQRXP0");
    assertThat(saved.getValue().getShareClassFigi()).isEqualTo("BBG001S5VPN7");
  }

  @Test
  void noPersisteCuandoElProveedorNoResuelve() {
    when(catalogRepository.findByAssetTypeInAndShareClassFigiIsNull(any()))
        .thenReturn(List.of(stock("ZZZZ")));
    when(instrumentIdentityPort.resolveByTicker(eq("ZZZZ"), anyString()))
        .thenReturn(Optional.empty());

    int resolved = service.backfillMissingIdentities();

    assertThat(resolved).isZero();
    verify(catalogRepository, never()).save(any());
  }

  @Test
  void noConsultaNadaCuandoNoHayPendientes() {
    when(catalogRepository.findByAssetTypeInAndShareClassFigiIsNull(any())).thenReturn(List.of());

    int resolved = service.backfillMissingIdentities();

    assertThat(resolved).isZero();
    verify(instrumentIdentityPort, never()).resolveByTicker(anyString(), anyString());
  }
}
