package com.mx.cryptomonitor.asset.application.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.port.out.CryptoLogoPort;
import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort;
import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort.IpoEntry;
import com.mx.cryptomonitor.asset.application.port.out.LogoResolverPort;
import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort;
import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort.StockProfile;
import com.mx.cryptomonitor.asset.domain.exception.CatalogFetchException;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogSyncService implements AssetCatalogRefreshPort {

  private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

  // IPOs con valor total de acciones por debajo de este umbral (USD) se ignoran para evitar
  // meter micro-listados al catalogo.
  private static final long IPO_MIN_TOTAL_SHARES_VALUE = 1_000_000_000L;

  // spothq/cryptocurrency-icons via jsDelivr: set determinista, sin API key, indexado por symbol.
  private static final String CRYPTO_ICON_BASE =
      "https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@master/128/color/";

  private static final List<AssetCatalogDto> STATIC_CRYPTOS =
      List.of(
          new AssetCatalogDto(
              "BTC", "Bitcoin", "CRYPTO", cryptoIconUrl("BTC"), null, "USD", 1_900_000L),
          new AssetCatalogDto(
              "ETH", "Ethereum", "CRYPTO", cryptoIconUrl("ETH"), null, "USD", 460_000L),
          new AssetCatalogDto(
              "USDT", "Tether", "CRYPTO", cryptoIconUrl("USDT"), null, "USD", 130_000L),
          new AssetCatalogDto("BNB", "BNB", "CRYPTO", cryptoIconUrl("BNB"), null, "USD", 88_000L),
          new AssetCatalogDto("XRP", "XRP", "CRYPTO", cryptoIconUrl("XRP"), null, "USD", 140_000L),
          new AssetCatalogDto(
              "USDC", "USD Coin", "CRYPTO", cryptoIconUrl("USDC"), null, "USD", 61_000L));

  private static String cryptoIconUrl(String symbol) {
    return CRYPTO_ICON_BASE + symbol.toLowerCase(Locale.ROOT) + ".png";
  }

  private static final List<AssetCatalogDto> STATIC_GOV_BONDS =
      List.of(
          new AssetCatalogDto(
              "CETES28", "CETES 28 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES91", "CETES 91 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES182", "CETES 182 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES364", "CETES 364 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "UDIBONO", "UDIBONO", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "BONDM", "Bonos M", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "TLT",
              "iShares 20+ Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "IEF",
              "iShares 7-10 Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "SHY",
              "iShares 1-3 Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "GOVT", "iShares US Treasury Bond ETF", "GOVERNMENT_BOND", null, null, "USD", null));

  private final CatalogFetchPort fmpAdapter;
  private final CatalogStorePort redisService;
  private final AssetCatalogRepository catalogRepository;
  private final LogoResolverPort logoResolver;
  private final StockProfilePort stockProfilePort;
  private final IpoCalendarPort ipoCalendarPort;
  private final CryptoLogoPort cryptoLogoPort;

  @Value("${finnhub.rate-limit.delay-ms:1100}")
  private long finnhubRateLimitDelayMs;

  // Ventana durante la cual un logo provisional (fallback determinista) no vuelve a preguntar al
  // proveedor primario. Al vencer, el siguiente acceso reintenta y hace auto-upgrade si el
  // proveedor
  // ya responde. Evita golpear al proveedor en cada lectura para activos sin logo autoritativo.
  @Value("${catalog.icon.fallback-retry-after:PT6H}")
  private Duration fallbackRetryAfter;

  @Scheduled(cron = "0 0 0 * * MON")
  public void syncWeekly() {
    log.info("CatalogSync: iniciando sync semanal");
    syncType("stock", 50);
    syncType("etf", 50);
    syncType("crypto", 50);
    syncType("government_bond", 50);
    log.info("CatalogSync: sync semanal completado");
  }

  /** Refresca el market cap real de cada STOCK del catalogo via Finnhub y reordena el ranking. */
  @Scheduled(cron = "0 0 6 * * *")
  public void syncDailyRanking() {
    log.info("CatalogSync: actualizando ranking diario con market cap real de Finnhub");
    List<AssetCatalogEntity> stocks = catalogRepository.findByAssetType("STOCK");
    if (stocks.isEmpty()) {
      log.warn("CatalogSync: catalogo STOCK vacio, ranking diario omitido");
      return;
    }

    int updated = 0;
    for (AssetCatalogEntity stock : stocks) {
      Long marketCap;
      try {
        marketCap =
            fetchStockProfile(stock.getSymbol())
                .map(StockProfile::marketCapMillions)
                .orElse(stock.getMarketCap());
      } catch (RuntimeException ex) {
        log.warn(
            "CatalogSync: fallo actualizando market cap de {}, se conserva el previo: {}",
            stock.getSymbol(),
            ex.getClass().getSimpleName());
        marketCap = stock.getMarketCap();
      }
      if (marketCap == null) {
        continue;
      }
      if (!marketCap.equals(stock.getMarketCap())) {
        stock.setMarketCap(marketCap);
        stock.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        catalogRepository.save(stock);
      }
      redisService.addToRanking("catalog:search:stock", stock.getSymbol(), marketCap);
      redisService.addToRanking("catalog:top10:stock", stock.getSymbol(), marketCap);
      updated++;
    }
    log.info(
        "CatalogSync: ranking diario actualizado, {} de {} simbolos STOCK con market cap Finnhub",
        updated,
        stocks.size());
  }

  /** Detecta IPOs recientes ejecutados ("priced") en NASDAQ/NYSE y los agrega al catalogo. */
  @Scheduled(cron = "0 0 7 * * MON")
  public void discoverNewListings() {
    log.info("CatalogSync: buscando nuevos listados (IPOs) via Finnhub");
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<IpoEntry> ipos;
    try {
      ipos = ipoCalendarPort.getRecentIpos(today.minusDays(30), today);
    } catch (RuntimeException ex) {
      log.error(
          "CatalogSync: fallo consultando calendario de IPOs, se omite el descubrimiento: {}",
          ex.getClass().getSimpleName());
      return;
    }

    List<IpoEntry> qualifying = ipos.stream().filter(this::qualifiesForCatalog).toList();

    int added = 0;
    for (IpoEntry ipo : qualifying) {
      if (catalogRepository.existsById(ipo.symbol())) {
        continue;
      }
      AssetCatalogDto dto = toDto(ipo);
      catalogRepository.save(toEntity(dto));
      redisService.saveEntry(dto);
      double score = dto.marketCap() != null ? dto.marketCap() : 0;
      redisService.addToRanking("catalog:search:stock", dto.symbol(), score);
      added++;
      log.info("CatalogSync: {} agregado al catalogo (IPO nuevo)", dto.symbol());
    }
    log.info("CatalogSync: descubrimiento de IPOs completado, {} nuevos listados agregados", added);
  }

  private boolean qualifiesForCatalog(IpoEntry ipo) {
    if (!"priced".equalsIgnoreCase(ipo.status())) {
      return false;
    }
    String exchange = ipo.exchange() == null ? "" : ipo.exchange().toUpperCase(Locale.ROOT);
    if (!exchange.contains("NASDAQ") && !exchange.contains("NYSE")) {
      return false;
    }
    return ipo.totalSharesValue() != null && ipo.totalSharesValue() > IPO_MIN_TOTAL_SHARES_VALUE;
  }

  private AssetCatalogDto toDto(IpoEntry ipo) {
    Optional<StockProfile> profile = fetchStockProfile(ipo.symbol());
    return new AssetCatalogDto(
        ipo.symbol(),
        profile.map(StockProfile::name).orElse(ipo.name()),
        "STOCK",
        profile.map(StockProfile::logoUrl).orElse(null),
        profile.map(StockProfile::exchange).orElse(ipo.exchange()),
        "USD",
        profile.map(StockProfile::marketCapMillions).orElse(null));
  }

  public void forceFullSync() {
    syncWeekly();
  }

  public void syncType(String type, int limit) {
    List<AssetCatalogDto> data;
    try {
      data = fetchFor(type, limit);
    } catch (CatalogFetchException e) {
      log.error(
          "Sync {} abortado por error FMP, catalogo previo conservado: {}", type, e.getMessage());
      return;
    }
    if (data.isEmpty()) {
      log.warn("Sync {} devolvio 0 resultados, conservando catalogo previo", type);
      return;
    }
    String searchKey = "catalog:search:" + type.toLowerCase(Locale.ROOT);
    String top10Key = "catalog:top10:" + type.toLowerCase(Locale.ROOT);
    int resolvedFromFinnhub = 0;
    Map<String, String> cryptoLogos =
        "crypto".equalsIgnoreCase(type)
            ? cryptoLogoPort.fetchLogosBySymbol(data.stream().map(AssetCatalogDto::symbol).toList())
            : Map.of();
    for (int i = 0; i < data.size(); i++) {
      AssetCatalogDto dto = enrichDto(data.get(i), cryptoLogos);
      if ("STOCK".equals(dto.assetType()) && dto.logoUrl() != null) {
        resolvedFromFinnhub++;
      }
      catalogRepository.save(toEntity(dto));
      redisService.saveEntry(dto);
      double score = dto.marketCap() != null ? dto.marketCap() : (limit - i);
      redisService.addToRanking(searchKey, dto.symbol(), score);
      if (i < 10) {
        redisService.addToRanking(top10Key, dto.symbol(), score);
      }
    }
    if ("stock".equalsIgnoreCase(type)) {
      log.info(
          "CatalogSync: {} de {} simbolos STOCK con logo/market cap resueltos via Finnhub",
          resolvedFromFinnhub,
          data.size());
    }
  }

  private AssetCatalogDto enrichDto(AssetCatalogDto dto, Map<String, String> cryptoLogos) {
    if ("STOCK".equals(dto.assetType())) {
      return enrichStock(dto);
    }
    if ("CRYPTO".equals(dto.assetType())) {
      return enrichCrypto(dto, cryptoLogos);
    }
    String logoUrl = resolveLogoUrl(dto.symbol(), dto.assetType(), dto.currency());
    if (logoUrl == null || logoUrl.equals(dto.logoUrl())) {
      return dto;
    }
    return new AssetCatalogDto(
        dto.symbol(),
        dto.name(),
        dto.assetType(),
        logoUrl,
        dto.exchange(),
        dto.currency(),
        dto.marketCap());
  }

  /**
   * CRYPTO: logo real de CoinGecko ({@code /coins/markets}, campo {@code image}). Fallback al valor
   * previo (jsDelivr de {@code STATIC_CRYPTOS}) cuando CoinGecko no devuelve imagen o falla.
   */
  private AssetCatalogDto enrichCrypto(AssetCatalogDto dto, Map<String, String> cryptoLogos) {
    String coinGeckoLogo = cryptoLogos.get(dto.symbol().toUpperCase(Locale.ROOT));
    if (coinGeckoLogo == null || coinGeckoLogo.isBlank() || coinGeckoLogo.equals(dto.logoUrl())) {
      return dto;
    }
    return new AssetCatalogDto(
        dto.symbol(),
        dto.name(),
        dto.assetType(),
        coinGeckoLogo,
        dto.exchange(),
        dto.currency(),
        dto.marketCap());
  }

  /** STOCK: Finnhub /profile2 aporta logo real y market cap real (reemplaza FMP para esto). */
  private AssetCatalogDto enrichStock(AssetCatalogDto dto) {
    Optional<StockProfile> profile = fetchStockProfile(dto.symbol());
    String logoUrl = profile.map(StockProfile::logoUrl).orElse(dto.logoUrl());
    Long marketCap = profile.map(StockProfile::marketCapMillions).orElse(dto.marketCap());
    return new AssetCatalogDto(
        dto.symbol(),
        dto.name(),
        dto.assetType(),
        logoUrl,
        dto.exchange(),
        dto.currency(),
        marketCap);
  }

  private Optional<StockProfile> fetchStockProfile(String symbol) {
    Optional<StockProfile> profile = stockProfilePort.getProfile(symbol);
    throttleFinnhubCall();
    return profile;
  }

  /** Finnhub free tier = 60 req/min; el catalogo tiene ~50-60 simbolos STOCK. */
  private void throttleFinnhubCall() {
    if (finnhubRateLimitDelayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(finnhubRateLimitDelayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Pull-once: cataloga el ícono de un símbolo aún no resuelto, en background. No-op si ya tiene
   * logo o si está marcado {@code NONE} (cache de negativos). Resuelve por tipo (CRYPTO →
   * CoinGecko; STOCK → Finnhub, con fallback determinista) y hace upsert en DB + Redis; si no hay
   * logo posible, marca {@code NONE} para no reintentar. Best-effort: cualquier fallo se traga
   * (DiscardPolicy).
   */
  @Async("assetIconExecutor")
  @Override
  public void ensureIconCatalogued(String symbol, String assetType) {
    resolveAndCacheOne(symbol, assetType);
  }

  @Override
  public Map<String, String> resolveMissingIcons(Map<String, String> symbolToType) {
    if (symbolToType == null || symbolToType.isEmpty()) {
      return Map.of();
    }
    Map<String, String> resolved = new HashMap<>();
    symbolToType.forEach(
        (symbol, type) -> {
          if (symbol == null || symbol.isBlank()) {
            return;
          }
          String upper = symbol.trim().toUpperCase(Locale.ROOT);
          if (resolved.containsKey(upper)) {
            return;
          }
          String url = resolveAndCacheOne(upper, type);
          if (url != null && !url.isBlank()) {
            resolved.put(upper, url);
          }
        });
    return resolved;
  }

  /**
   * Núcleo pull-once para un símbolo: no-op si ya está resuelto o marcado NONE (cache de
   * negativos); si no, resuelve por tipo (CRYPTO → CoinGecko → jsDelivr; STOCK → Finnhub →
   * CompaniesLogo) y hace upsert (RESOLVED o NONE). Devuelve la URL del logo o {@code null} si no
   * hay logo posible.
   */
  private String resolveAndCacheOne(String symbol, String assetType) {
    if (symbol == null || symbol.isBlank() || assetType == null || assetType.isBlank()) {
      return null;
    }
    String upper = symbol.trim().toUpperCase(Locale.ROOT);
    String type = assetType.trim().toUpperCase(Locale.ROOT);

    Optional<AssetCatalogEntity> existing = catalogRepository.findById(upper);
    if (existing.isPresent()) {
      AssetCatalogEntity entity = existing.get();
      if ("NONE".equals(entity.getLogoStatus())) {
        return null; // cache de negativos: no hay logo posible
      }
      boolean hasLogo = entity.getLogoUrl() != null && !entity.getLogoUrl().isBlank();
      if (hasLogo && !isProvisional(entity, type)) {
        return entity.getLogoUrl(); // logo autoritativo del proveedor: resuelto para siempre
      }
      // El throttle solo aplica a filas FALLBACK (el proveedor primario ya se intentó y falló hace
      // poco), no a las provisionales-por-heurística (jsDelivr marcado RESOLVED por migración,
      // nunca
      // intentado por este path): esas se upgradean en el primer acceso, sin esperar la ventana.
      if (hasLogo && "FALLBACK".equals(entity.getLogoStatus()) && !fallbackRetryDue(entity)) {
        return entity
            .getLogoUrl(); // ya intentado y fallido dentro de la ventana: aun no repreguntar
      }
      // provisional elegible para upgrade (o sin logo aun): se recae al proveedor primario abajo,
      // para hacer auto-upgrade del fallback al logo autoritativo en cuanto el proveedor responda.
    }

    AssetCatalogDto base =
        existing
            .map(
                e ->
                    new AssetCatalogDto(
                        e.getSymbol(),
                        e.getName(),
                        e.getAssetType(),
                        e.getLogoUrl(),
                        e.getExchange(),
                        e.getCurrency(),
                        e.getMarketCap()))
            .orElseGet(() -> new AssetCatalogDto(upper, upper, type, null, null, null, null));

    IconResolution resolution =
        switch (type) {
          case "CRYPTO" -> resolveCryptoLogoOnDemand(base, upper);
          case "STOCK" -> resolveStockLogoOnDemand(base, upper);
          default -> new IconResolution(
              base, base.logoUrl() != null && !base.logoUrl().isBlank() ? "RESOLVED" : "NONE");
        };

    AssetCatalogDto resolved = resolution.dto();
    if (resolved.logoUrl() == null || resolved.logoUrl().isBlank()) {
      // Sin logo posible: marca NONE (cache de negativos) para no reintentar en cada request.
      catalogRepository.save(toEntity(resolved, "NONE"));
      return null;
    }

    catalogRepository.save(toEntity(resolved, resolution.status()));
    redisService.saveEntry(resolved);
    if ("RESOLVED".equals(resolution.status())) {
      log.info("CatalogSync: icono resuelto on-demand para {} ({})", upper, type);
    } else {
      log.info(
          "CatalogSync: icono provisional (fallback) para {} ({}); se reintentara el proveedor",
          upper,
          type);
    }
    return resolved.logoUrl();
  }

  /**
   * CRYPTO on-demand: CoinGecko primario ({@code RESOLVED}) → jsDelivr determinista como fallback
   * provisional ({@code FALLBACK}), que se auto-actualiza a CoinGecko en un acceso posterior.
   */
  private IconResolution resolveCryptoLogoOnDemand(AssetCatalogDto base, String symbol) {
    String coinGecko = cryptoLogoPort.fetchLogosBySymbol(List.of(symbol)).get(symbol);
    if (coinGecko != null && !coinGecko.isBlank()) {
      return new IconResolution(withLogo(base, coinGecko), "RESOLVED");
    }
    return new IconResolution(withLogo(base, cryptoIconUrl(symbol)), "FALLBACK");
  }

  private IconResolution resolveStockLogoOnDemand(AssetCatalogDto base, String symbol) {
    AssetCatalogDto enriched = enrichStock(base);
    if (enriched.logoUrl() != null && !enriched.logoUrl().isBlank()) {
      return new IconResolution(enriched, "RESOLVED");
    }
    // CompaniesLogo es determinista y estable para STOCK: se trata como autoritativo (sin reintento
    // bloqueante contra Finnhub en el hot path por su rate limit de 60 req/min).
    return new IconResolution(withLogo(enriched, logoResolver.buildLogoUrl(symbol)), "RESOLVED");
  }

  /**
   * Un logo es provisional cuando NO proviene del proveedor autoritativo sino de un fallback
   * determinista (jsDelivr para CRYPTO). Se marca {@code logo_status=FALLBACK}, pero también se
   * detecta por la URL para filas escritas antes de introducir ese estado (auto-upgrade
   * retroactivo).
   */
  private boolean isProvisional(AssetCatalogEntity entity, String type) {
    if ("FALLBACK".equals(entity.getLogoStatus())) {
      return true;
    }
    return "CRYPTO".equals(type)
        && entity.getLogoUrl() != null
        && entity.getLogoUrl().startsWith(CRYPTO_ICON_BASE);
  }

  /** ¿Toca reintentar el proveedor primario para un logo provisional? (throttle por ventana). */
  private boolean fallbackRetryDue(AssetCatalogEntity entity) {
    if (fallbackRetryAfter == null
        || fallbackRetryAfter.isZero()
        || fallbackRetryAfter.isNegative()) {
      return true;
    }
    OffsetDateTime checkedAt = entity.getLogoCheckedAt();
    if (checkedAt == null) {
      return true;
    }
    return checkedAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minus(fallbackRetryAfter));
  }

  private record IconResolution(AssetCatalogDto dto, String status) {}

  private AssetCatalogDto withLogo(AssetCatalogDto dto, String logoUrl) {
    if (logoUrl == null || logoUrl.isBlank()) {
      return dto;
    }
    return new AssetCatalogDto(
        dto.symbol(),
        dto.name(),
        dto.assetType(),
        logoUrl,
        dto.exchange(),
        dto.currency(),
        dto.marketCap());
  }

  private String resolveLogoUrl(String symbol, String assetType, String currency) {
    return switch (assetType) {
      case "ETF" -> logoResolver.buildLogoUrl(symbol);
      case "GOVERNMENT_BOND" -> "USD".equals(currency) ? logoResolver.buildLogoUrl(symbol) : null;
      default -> null; // STOCK -> enrichStock (Finnhub); CRYPTO -> enrichCrypto (CoinGecko);
        // INDEX/bonos MX sin logo
    };
  }

  // Kept for backward compatibility with existing callers/tests.
  public void syncStocks(int limit) {
    List<AssetCatalogDto> stocks = fmpAdapter.fetchTopStocks(limit);
    if (stocks.isEmpty()) {
      log.warn("CatalogSync: FMP devolvio 0 stocks");
      return;
    }
    for (int i = 0; i < stocks.size(); i++) {
      AssetCatalogDto dto = stocks.get(i);
      redisService.saveEntry(dto);
      redisService.addToRanking(
          "catalog:search:stock",
          dto.symbol(),
          dto.marketCap() != null ? dto.marketCap() : (limit - i));
      if (i < 10) {
        redisService.addToRanking(
            "catalog:top10:stock",
            dto.symbol(),
            dto.marketCap() != null ? dto.marketCap() : (10 - i));
      }
    }
  }

  // Kept for backward compatibility with existing callers/tests.
  public void syncEtfs(int limit) {
    List<AssetCatalogDto> etfs = fmpAdapter.fetchTopEtfs(limit);
    if (etfs.isEmpty()) {
      log.warn("CatalogSync: FMP devolvio 0 ETFs");
      return;
    }
    for (int i = 0; i < etfs.size(); i++) {
      AssetCatalogDto dto = etfs.get(i);
      redisService.saveEntry(dto);
      redisService.addToRanking("catalog:search:etf", dto.symbol(), limit - i);
      if (i < 10) redisService.addToRanking("catalog:top10:etf", dto.symbol(), 10 - i);
    }
  }

  private List<AssetCatalogDto> fetchFor(String type, int limit) {
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "stock" -> fmpAdapter.fetchTopStocks(limit);
      case "etf" -> fmpAdapter.fetchTopEtfs(limit);
      case "crypto" -> STATIC_CRYPTOS;
      case "government_bond" -> STATIC_GOV_BONDS;
      default -> List.of();
    };
  }

  private AssetCatalogEntity toEntity(AssetCatalogDto dto) {
    boolean hasLogo = dto.logoUrl() != null && !dto.logoUrl().isBlank();
    return toEntity(dto, hasLogo ? "RESOLVED" : "NONE");
  }

  private AssetCatalogEntity toEntity(AssetCatalogDto dto, String logoStatus) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return AssetCatalogEntity.builder()
        .symbol(dto.symbol())
        .name(dto.name())
        .assetType(dto.assetType())
        .logoUrl(dto.logoUrl())
        .exchange(dto.exchange())
        .currency(dto.currency())
        .marketCap(dto.marketCap())
        .popular(false)
        .logoStatus(logoStatus)
        .logoCheckedAt(now)
        .updatedAt(now)
        .build();
  }
}
