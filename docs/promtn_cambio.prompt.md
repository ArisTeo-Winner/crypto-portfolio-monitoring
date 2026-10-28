Contexto: Backend Spring Boot 3.5 / Java 21, arquitectura hexagonal, módulos
(user, portfolio, transaction, asset, marketdata, shared). PostgreSQL 16,
Flyway, Redis (Lettuce), WebClient reactivo. Formatear con ./mvnw spotless:apply
antes de cada commit. Seguir convenciones de CLAUDE.md.

Aplicar los siguientes cambios en orden estricto.

═══════════════════════════════════════════════════════════════════════════
CAMBIO 1 — Flyway: nuevos campos en transaction + tabla dividend_detail
═══════════════════════════════════════════════════════════════════════════

Crear:
  src/main/resources/db/migration/V2026_06_10_01__add_transaction_fields_and_dividend_detail.sql

-- Nuevos campos en transaction
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS asset_name VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS exchange   VARCHAR(20)  NULL,
    ADD COLUMN IF NOT EXISTS broker     VARCHAR(50)  NULL,
    ADD COLUMN IF NOT EXISTS currency   VARCHAR(3)   NULL;

COMMENT ON COLUMN transaction.asset_name IS 'Nombre completo del activo (ej: NVIDIA Corporation)';
COMMENT ON COLUMN transaction.exchange   IS 'Bolsa de origen (ej: NASDAQGS, BMV, NYSE)';
COMMENT ON COLUMN transaction.broker     IS 'Broker/plataforma (ej: GBM, IBKR, Binance)';
COMMENT ON COLUMN transaction.currency   IS 'Moneda ISO-4217 de la transaccion (USD, MXN, EUR)';

-- Tabla dividend_detail
CREATE TABLE IF NOT EXISTS dividend_detail (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID          NOT NULL
                                   REFERENCES transaction(transaction_id)
                                   ON DELETE CASCADE,
    ex_dividend_date DATE          NULL,
    dividend_type    VARCHAR(10)   NOT NULL DEFAULT 'CASH'
                                   CHECK (dividend_type IN ('CASH','STOCK')),
    tax_withheld     NUMERIC(18,8) NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dividend_detail_transaction_id
    ON dividend_detail(transaction_id);

COMMENT ON TABLE  dividend_detail                  IS 'Detalle de transacciones DIVIDEND';
COMMENT ON COLUMN dividend_detail.ex_dividend_date IS 'Fecha ex-dividendo';
COMMENT ON COLUMN dividend_detail.dividend_type    IS 'CASH=efectivo, STOCK=reinversion DRIP';
COMMENT ON COLUMN dividend_detail.tax_withheld     IS 'Retencion fiscal (ej: 30% retencion EE.UU.)';

═══════════════════════════════════════════════════════════════════════════
CAMBIO 2 — Unificar enum AssetType (BONOS→BOND, INDICE→INDEX)
═══════════════════════════════════════════════════════════════════════════

Buscar todos los archivos Java que contengan "BONOS" o "INDICE" como valores
de enum o strings literales. En cada enum AssetType encontrado:

  Renombrar BONOS  → BOND
  Renombrar INDICE → INDEX
  Valores finales: CRYPTO, STOCK, ETF, BOND, INDEX

Si hay CHECK CONSTRAINT en columnas asset_type, crear migración adicional:
  V2026_06_10_02__rename_asset_type_values.sql

  UPDATE transaction SET asset_type = 'BOND'  WHERE asset_type = 'BONOS';
  UPDATE transaction SET asset_type = 'INDEX' WHERE asset_type = 'INDICE';

  -- Si hay CHECK CONSTRAINT, alterarlo para aceptar nuevos valores.

Actualizar también cualquier test que referencie BONOS o INDICE.

═══════════════════════════════════════════════════════════════════════════
CAMBIO 3 — Entidad Transaction: añadir 4 nuevos campos
═══════════════════════════════════════════════════════════════════════════

Archivo: transaction/domain/model/Transaction.java

Añadir con anotaciones JPA:

  @Column(name = "asset_name", length = 255)
  private String assetName;

  @Column(name = "exchange", length = 20)
  private String exchange;

  @Column(name = "broker", length = 50)
  private String broker;

  @Column(name = "currency", length = 3)
  private String currency;

═══════════════════════════════════════════════════════════════════════════
CAMBIO 4 — DTOs de transacción: añadir nuevos campos
═══════════════════════════════════════════════════════════════════════════

En BuyTransactionRequest, SellTransactionRequest y cualquier otro request
de transacción existente, añadir al final del record (campos opcionales):

  String assetName    // null si no se envía; backend lo resuelve desde catálogo
  String exchange     // ej: "NASDAQGS", "BMV"
  String broker       // ej: "GBM", "IBKR", "Binance"
  String currency     // ej: "USD", "MXN"

En TransactionResponse añadir los mismos 4 campos.

Si existe MapStruct mapper Transaction → TransactionResponse, actualizar
el mapeo para incluir los nuevos campos.

═══════════════════════════════════════════════════════════════════════════
CAMBIO 5 — DIVIDEND: enum, entidad, repositorio, DTO, endpoint
═══════════════════════════════════════════════════════════════════════════

5a. Añadir DIVIDEND al enum TransactionType.

5b. Crear transaction/domain/model/DividendType.java:

  public enum DividendType { CASH, STOCK }

5c. Crear transaction/domain/model/DividendDetail.java:

  @Entity @Table(name = "dividend_detail")
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public class DividendDetail {
      @Id @GeneratedValue(strategy = GenerationType.UUID)
      @Column(name = "id", nullable = false, updatable = false)
      private UUID id;

      @OneToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "transaction_id", nullable = false, unique = true)
      private Transaction transaction;

      @Column(name = "ex_dividend_date")
      private LocalDate exDividendDate;

      @Enumerated(EnumType.STRING)
      @Column(name = "dividend_type", nullable = false, length = 10)
      private DividendType dividendType;

      @Column(name = "tax_withheld", precision = 18, scale = 8)
      private BigDecimal taxWithheld;

      @Column(name = "created_at", nullable = false)
      private OffsetDateTime createdAt;
  }

5d. Crear transaction/domain/repository/DividendDetailRepository.java:

  public interface DividendDetailRepository extends JpaRepository<DividendDetail, UUID> {
      Optional<DividendDetail> findByTransactionTransactionId(UUID transactionId);
  }

5e. Crear transaction/application/dto/request/DividendTransactionRequest.java:

  public record DividendTransactionRequest(
      @NotBlank  String assetSymbol,
                 String assetName,
      @NotBlank  String assetType,
      @NotNull @Positive BigDecimal amount,
      @NotNull   OffsetDateTime transactionDate,
                 LocalDate exDividendDate,
                 String dividendType,       // "CASH" o "STOCK", default CASH
                 BigDecimal taxWithheld,
                 String broker,
                 String currency,
                 String exchange
  ) {}

5f. En el controlador de transacciones añadir:

  @PostMapping("/dividend")
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponse registerDividend(
          @RequestHeader("X-Idempotency-Key") String idempotencyKey,
          @Valid @RequestBody DividendTransactionRequest request,
          @AuthenticationPrincipal UserDetails userDetails,
          HttpServletRequest httpRequest) {
      return transactionService.registerDividend(request, idempotencyKey, userDetails, httpRequest);
  }

5g. Implementar registerDividend en el servicio de transacciones:
  - Aplicar idempotencia via TransactionIdempotencyService (igual que BUY/SELL)
  - Crear Transaction con transactionType=DIVIDEND, quantity=0,
    pricePerUnit=0, totalValue=request.amount()
  - Crear DividendDetail asociado con los campos del request
  - dividendType default CASH si viene null
  - Registrar audit log (AUTH pattern de CLAUDE.md)

═══════════════════════════════════════════════════════════════════════════
CAMBIO 6 — CompaniesLogoAdapter: logos para ETF y BOND
═══════════════════════════════════════════════════════════════════════════

Crear:
  asset/infrastructure/outbound/companieslogo/CompaniesLogoAdapter.java

  @Component
  public class CompaniesLogoAdapter {

      private static final Logger log = LoggerFactory.getLogger(CompaniesLogoAdapter.class);
      private static final String BASE = "https://companieslogo.com";

      /**
       * Construye la URL del logo sin llamada HTTP previa.
       * CompaniesLogo usa URLs predecibles; el frontend maneja 404 con placeholder.
       */
      public String buildLogoUrl(String ticker) {
          if (ticker == null || ticker.isBlank()) return null;
          return BASE + "/api/starter/stock-symbol/" + ticker.toUpperCase();
      }
  }

No se requiere WebClient ni API key. La URL es determinista.

En application.properties NO añadir nada para CompaniesLogo (no requiere config).

═══════════════════════════════════════════════════════════════════════════
CAMBIO 7 — Arquitectura Redis para catálogo dinámico de activos
═══════════════════════════════════════════════════════════════════════════

Reemplazar completamente el List<AssetCatalogEntry> CATALOG hardcodeado
en AssetSearchService por lecturas desde Redis.

── 7a. Claves Redis a usar ──────────────────────────────────────────────

  catalog:top10:stock    → Sorted Set (score=marketCap en millones)
  catalog:top10:etf      → Sorted Set (score=aum en millones)
  catalog:top10:crypto   → Sorted Set (score=marketCap en millones)

  catalog:search:stock   → Sorted Set top 50 (score=marketCap)
  catalog:search:etf     → Sorted Set top 50 (score=aum)
  catalog:search:crypto  → Sorted Set top 50 (score=marketCap)

  catalog:entry:{SYMBOL} → Hash con campos:
                             symbol, name, assetType, logoUrl,
                             exchange, currency, marketCap, updatedAt

  catalog:search:misc:{SYMBOL} → Hash (on-demand, TTL 24h)
                                   mismos campos que catalog:entry

── 7b. CatalogRedisService ──────────────────────────────────────────────

Crear: asset/infrastructure/outbound/redis/CatalogRedisService.java

  @Service
  @RequiredArgsConstructor
  public class CatalogRedisService {

      private final StringRedisTemplate redisTemplate;

      public void saveEntry(AssetCatalogDto entry) {
          String key = "catalog:entry:" + entry.symbol().toUpperCase();
          Map<String, String> fields = Map.of(
              "symbol",    entry.symbol(),
              "name",      entry.name(),
              "assetType", entry.assetType(),
              "logoUrl",   entry.logoUrl() != null ? entry.logoUrl() : "",
              "exchange",  entry.exchange() != null ? entry.exchange() : "",
              "currency",  entry.currency() != null ? entry.currency() : "",
              "marketCap", entry.marketCap() != null ? entry.marketCap().toString() : "0",
              "updatedAt", OffsetDateTime.now().toString()
          );
          redisTemplate.opsForHash().putAll(key, fields);
      }

      public void addToRanking(String rankingKey, String symbol, double score) {
          redisTemplate.opsForZSet().add(rankingKey, symbol, score);
      }

      public List<String> getTopSymbols(String rankingKey, int limit) {
          Set<String> result = redisTemplate.opsForZSet()
              .reverseRange(rankingKey, 0, limit - 1);
          return result != null ? new ArrayList<>(result) : List.of();
      }

      public Optional<AssetCatalogDto> findEntry(String symbol) {
          String key = "catalog:entry:" + symbol.toUpperCase();
          Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
          if (fields.isEmpty()) {
              // Intentar misc (on-demand)
              key = "catalog:search:misc:" + symbol.toUpperCase();
              fields = redisTemplate.opsForHash().entries(key);
          }
          return fields.isEmpty() ? Optional.empty() : Optional.of(mapToDto(fields));
      }

      public boolean isCatalogLoaded() {
          Long size = redisTemplate.opsForZSet().size("catalog:search:stock");
          return size != null && size > 0;
      }

      public void saveMiscEntry(AssetCatalogDto entry, Duration ttl) {
          String key = "catalog:search:misc:" + entry.symbol().toUpperCase();
          Map<String, String> fields = /* igual que saveEntry */;
          redisTemplate.opsForHash().putAll(key, fields);
          redisTemplate.expire(key, ttl);
      }

      private AssetCatalogDto mapToDto(Map<Object, Object> fields) {
          return new AssetCatalogDto(
              (String) fields.get("symbol"),
              (String) fields.get("name"),
              (String) fields.get("assetType"),
              nullIfBlank((String) fields.get("logoUrl")),
              nullIfBlank((String) fields.get("exchange")),
              nullIfBlank((String) fields.get("currency")),
              parseLong((String) fields.get("marketCap"))
          );
      }

      private String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }
      private Long parseLong(String s) {
          try { return s != null ? Long.parseLong(s) : null; } catch (NumberFormatException e) { return null; }
      }
  }

── 7c. AssetCatalogDto ──────────────────────────────────────────────────

Crear: asset/application/dto/AssetCatalogDto.java

  public record AssetCatalogDto(
      String symbol,
      String name,
      String assetType,
      String logoUrl,
      String exchange,
      String currency,
      Long   marketCap
  ) {}

── 7d. Fallback hardcodeado (Redis + API caídos) ────────────────────────

Crear: asset/application/service/AssetCatalogFallback.java

  @Component
  public class AssetCatalogFallback {

      public static final List<AssetCatalogDto> ENTRIES = List.of(
          new AssetCatalogDto("bitcoin",  "BTC", "Bitcoin",      "CRYPTO", null, null, null),
          new AssetCatalogDto("ethereum", "ETH", "Ethereum",     "CRYPTO", null, null, null),
          new AssetCatalogDto("tether",  "USDT", "Tether USDt",  "CRYPTO", null, null, null),
          new AssetCatalogDto("ripple",   "XRP", "XRP",          "CRYPTO", null, null, null),
          new AssetCatalogDto("bnb",      "BNB", "BNB",          "CRYPTO", null, null, null),
          new AssetCatalogDto("apple",   "AAPL", "Apple Inc.",   "STOCK",  null, "USD", null),
          new AssetCatalogDto("nvidia",  "NVDA", "NVIDIA Corp",  "STOCK",  null, "USD", null),
          new AssetCatalogDto("msft",    "MSFT", "Microsoft",    "STOCK",  null, "USD", null),
          new AssetCatalogDto("google",  "GOOGL","Alphabet Inc.","STOCK",  null, "USD", null),
          new AssetCatalogDto("voo",      "VOO", "Vanguard S&P 500 ETF",   "ETF", null, "USD", null),
          new AssetCatalogDto("qqq",      "QQQ", "Invesco QQQ Trust",      "ETF", null, "USD", null)
      );
  }

═══════════════════════════════════════════════════════════════════════════
CAMBIO 8 — FMP Adapter: fuente de datos para sync del catálogo
═══════════════════════════════════════════════════════════════════════════

Crear: asset/infrastructure/outbound/fmp/FmpCatalogAdapter.java

  @Component
  public class FmpCatalogAdapter {

      private static final Logger log = LoggerFactory.getLogger(FmpCatalogAdapter.class);
      private static final Duration TIMEOUT = Duration.ofSeconds(10);

      private final WebClient webClient;
      private final String apiKey;
      private final CompaniesLogoAdapter companiesLogoAdapter;

      public FmpCatalogAdapter(
              WebClient.Builder builder,
              @Value("${fmp.base-url:https://financialmodelingprep.com}") String baseUrl,
              @Value("${fmp.api-key}") String apiKey,
              CompaniesLogoAdapter companiesLogoAdapter) {
          this.webClient = builder.baseUrl(baseUrl).build();
          this.apiKey = apiKey;
          this.companiesLogoAdapter = companiesLogoAdapter;
      }

      /** Top 50 stocks por market cap */
      public List<AssetCatalogDto> fetchTopStocks(int limit) {
          try {
              List<FmpStockScreenerItem> items = webClient.get()
                  .uri(u -> u.path("/api/v3/stock-screener")
                      .queryParam("marketCapMoreThan", 100_000_000_000L)
                      .queryParam("exchange", "NASDAQ,NYSE")
                      .queryParam("limit", limit)
                      .queryParam("apikey", apiKey)
                      .build())
                  .retrieve()
                  .bodyToFlux(FmpStockScreenerItem.class)
                  .collectList()
                  .timeout(TIMEOUT)
                  .block();

              if (items == null) return List.of();

              return items.stream().map(i -> new AssetCatalogDto(
                  i.symbol(), i.companyName(), "STOCK",
                  null,           // logoUrl: Finnhub lo provee para STOCK
                  i.exchangeShortName(), "USD",
                  i.marketCap() != null ? i.marketCap() / 1_000_000 : null
              )).toList();
          } catch (Exception e) {
              log.warn("FMP fetchTopStocks failed: {}", e.getMessage());
              return List.of();
          }
      }

      /** Top 50 ETFs por AUM */
      public List<AssetCatalogDto> fetchTopEtfs(int limit) {
          try {
              List<FmpEtfItem> items = webClient.get()
                  .uri(u -> u.path("/api/v3/etf/list")
                      .queryParam("apikey", apiKey)
                      .build())
                  .retrieve()
                  .bodyToFlux(FmpEtfItem.class)
                  .take(limit)
                  .collectList()
                  .timeout(TIMEOUT)
                  .block();

              if (items == null) return List.of();

              return items.stream().map(i -> new AssetCatalogDto(
                  i.symbol(), i.name(), "ETF",
                  companiesLogoAdapter.buildLogoUrl(i.symbol()),
                  "NASDAQ", "USD", null
              )).toList();
          } catch (Exception e) {
              log.warn("FMP fetchTopEtfs failed: {}", e.getMessage());
              return List.of();
          }
      }

      // Records internos para deserialización FMP
      private record FmpStockScreenerItem(
          String symbol, String companyName, String exchangeShortName, Long marketCap) {}

      private record FmpEtfItem(String symbol, String name) {}
  }

En application.properties añadir:
  fmp.base-url=https://financialmodelingprep.com
  fmp.api-key=${FMP_API_KEY}

En .env o variables de entorno del servidor: FMP_API_KEY=tu_clave_aqui

═══════════════════════════════════════════════════════════════════════════
CAMBIO 9 — CatalogSyncService: jobs diario y semanal
═══════════════════════════════════════════════════════════════════════════

Crear: asset/application/service/CatalogSyncService.java

  @Service
  @RequiredArgsConstructor
  public class CatalogSyncService {

      private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

      private final FmpCatalogAdapter fmpAdapter;
      private final CatalogRedisService redisService;
      private final CoinGeckoCatalogAdapter coinGeckoAdapter; // adaptador existente o nuevo
      private final CompaniesLogoAdapter companiesLogoAdapter;

      /**
       * Semanal — lunes 00:00. Actualiza catálogo completo top 50.
       */
      @Scheduled(cron = "0 0 0 * * MON")
      public void syncWeekly() {
          log.info("CatalogSync: iniciando sync semanal");
          syncStocks(50);
          syncEtfs(50);
          syncCryptos(50);
          log.info("CatalogSync: sync semanal completado");
      }

      /**
       * Diario — 06:00 AM. Actualiza ranking top 10 (scores por market cap).
       */
      @Scheduled(cron = "0 0 6 * * *")
      public void syncDailyRanking() {
          log.info("CatalogSync: actualizando ranking diario top 10");
          List<AssetCatalogDto> stocks = fmpAdapter.fetchTopStocks(10);
          stocks.forEach(dto -> {
              redisService.addToRanking(
                  "catalog:top10:stock", dto.symbol(),
                  dto.marketCap() != null ? dto.marketCap() : 0);
          });
          // ETF y crypto: misma lógica
      }

      public void syncStocks(int limit) {
          List<AssetCatalogDto> stocks = fmpAdapter.fetchTopStocks(limit);
          if (stocks.isEmpty()) { log.warn("CatalogSync: FMP devolvio 0 stocks"); return; }
          for (int i = 0; i < stocks.size(); i++) {
              AssetCatalogDto dto = stocks.get(i);
              redisService.saveEntry(dto);
              redisService.addToRanking("catalog:search:stock", dto.symbol(),
                  dto.marketCap() != null ? dto.marketCap() : (limit - i));
              if (i < 10) {
                  redisService.addToRanking("catalog:top10:stock", dto.symbol(),
                      dto.marketCap() != null ? dto.marketCap() : (10 - i));
              }
          }
      }

      public void syncEtfs(int limit) {
          List<AssetCatalogDto> etfs = fmpAdapter.fetchTopEtfs(limit);
          if (etfs.isEmpty()) { log.warn("CatalogSync: FMP devolvio 0 ETFs"); return; }
          for (int i = 0; i < etfs.size(); i++) {
              AssetCatalogDto dto = etfs.get(i);
              redisService.saveEntry(dto);
              redisService.addToRanking("catalog:search:etf", dto.symbol(), limit - i);
              if (i < 10) redisService.addToRanking("catalog:top10:etf", dto.symbol(), 10 - i);
          }
      }

      public void syncCryptos(int limit) {
          // Usar CoinGecko /coins/markets?vs_currency=usd&order=market_cap_desc&per_page=50
          // Mismo patrón que syncStocks
      }

      /** Llamado externamente para carga inicial (warm-up y admin endpoint) */
      public void forceFullSync() {
          syncWeekly();
      }
  }

═══════════════════════════════════════════════════════════════════════════
CAMBIO 10 — CatalogWarmUpRunner: carga Redis al arrancar la app
═══════════════════════════════════════════════════════════════════════════

Crear: asset/infrastructure/configuration/CatalogWarmUpRunner.java

  @Component
  @RequiredArgsConstructor
  public class CatalogWarmUpRunner implements ApplicationRunner {

      private static final Logger log = LoggerFactory.getLogger(CatalogWarmUpRunner.class);

      private final CatalogRedisService redisService;
      private final CatalogSyncService syncService;

      @Override
      public void run(ApplicationArguments args) {
          if (!redisService.isCatalogLoaded()) {
              log.info("CatalogWarmUp: Redis vacio, cargando catalogo...");
              try {
                  syncService.forceFullSync();
                  log.info("CatalogWarmUp: catalogo cargado exitosamente");
              } catch (Exception e) {
                  log.warn("CatalogWarmUp: fallo carga desde API, usando fallback hardcodeado");
                  AssetCatalogFallback.ENTRIES.forEach(dto -> redisService.saveEntry(dto));
              }
          } else {
              log.info("CatalogWarmUp: catalogo ya presente en Redis, omitiendo carga");
          }
      }
  }

═══════════════════════════════════════════════════════════════════════════
CAMBIO 11 — Refactorizar AssetSearchService para leer desde Redis
═══════════════════════════════════════════════════════════════════════════

Reemplazar AssetSearchService completamente:

  @Service
  @RequiredArgsConstructor
  public class AssetSearchService implements AssetCatalogQueryPort {

      private static final int DEFAULT_LIMIT = 10;
      private static final int MAX_LIMIT = 20;
      private static final Duration MISC_TTL = Duration.ofHours(24);

      private final CatalogRedisService redisService;
      private final FmpCatalogAdapter fmpAdapter;
      private final AssetCatalogFallback fallback;

      public AssetSearchResponse search(String query, Integer limit) {
          String q = normalizeQuery(query);
          int lim = normalizeLimit(limit);
          String lower = q.toLowerCase(Locale.ROOT);

          // 1. Buscar en catalog:search:stock + etf + crypto
          List<AssetCatalogDto> candidates = searchInRedis(lower);

          // 2. Si no hay resultados, intentar lookup on-demand via FMP
          if (candidates.isEmpty()) {
              candidates = lookupOnDemand(q.toUpperCase());
          }

          // 3. Si todo falla, buscar en fallback
          if (candidates.isEmpty()) {
              candidates = searchInFallback(lower);
          }

          List<AssetOptionResponse> items = candidates.stream()
              .sorted(searchComparator(lower))
              .limit(lim)
              .map(this::toResponse)
              .toList();

          return new AssetSearchResponse(items, items.size(), q);
      }

      @Override
      public Optional<String> findAssetIdBySymbol(String symbol) {
          return redisService.findEntry(symbol).map(AssetCatalogDto::symbol);
      }

      public Optional<String> findNameBySymbol(String symbol) {
          return redisService.findEntry(symbol).map(AssetCatalogDto::name);
      }

      /** Devuelve top 10 por tipo para la sección "Populares" del modal */
      public List<AssetOptionResponse> getPopular(String assetType) {
          String key = "catalog:top10:" + assetType.toLowerCase();
          return redisService.getTopSymbols(key, 10).stream()
              .map(symbol -> redisService.findEntry(symbol))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .map(this::toResponse)
              .toList();
      }

      private List<AssetCatalogDto> searchInRedis(String lower) {
          List<String> allKeys = List.of(
              "catalog:search:stock", "catalog:search:etf", "catalog:search:crypto");
          List<AssetCatalogDto> results = new ArrayList<>();
          for (String key : allKeys) {
              redisService.getTopSymbols(key, 50).stream()
                  .map(symbol -> redisService.findEntry(symbol))
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .filter(dto -> matches(dto, lower))
                  .forEach(results::add);
          }
          return results;
      }

      private List<AssetCatalogDto> lookupOnDemand(String symbol) {
          // Intentar FMP para stock o ETF desconocido
          List<AssetCatalogDto> found = fmpAdapter.fetchTopStocks(1).stream()
              .filter(d -> d.symbol().equalsIgnoreCase(symbol))
              .toList();
          if (!found.isEmpty()) {
              redisService.saveMiscEntry(found.get(0), MISC_TTL);
          }
          return found;
      }

      private List<AssetCatalogDto> searchInFallback(String lower) {
          return AssetCatalogFallback.ENTRIES.stream()
              .filter(dto -> matches(dto, lower))
              .toList();
      }

      private boolean matches(AssetCatalogDto dto, String lower) {
          return dto.symbol().toLowerCase(Locale.ROOT).contains(lower)
              || dto.name().toLowerCase(Locale.ROOT).contains(lower);
      }

      private Comparator<AssetCatalogDto> searchComparator(String lower) {
          return Comparator.comparing(
                  (AssetCatalogDto d) -> !d.symbol().equalsIgnoreCase(lower))
              .thenComparing((AssetCatalogDto d) -> !d.name().equalsIgnoreCase(lower))
              .thenComparing(AssetCatalogDto::symbol);
      }

      private AssetOptionResponse toResponse(AssetCatalogDto dto) {
          return new AssetOptionResponse(
              dto.symbol().toLowerCase(),
              dto.symbol(),
              dto.name(),
              dto.assetType(),
              dto.logoUrl(),
              !"INDEX".equals(dto.assetType()));
      }

      private String normalizeQuery(String query) {
          if (query == null || query.trim().isEmpty())
              throw new IllegalArgumentException("Query parameter q is required.");
          return query.trim();
      }

      private int normalizeLimit(Integer limit) {
          if (limit == null) return DEFAULT_LIMIT;
          if (limit < 1 || limit > MAX_LIMIT)
              throw new IllegalArgumentException("Query parameter limit must be between 1 and 20.");
          return limit;
      }
  }

═══════════════════════════════════════════════════════════════════════════
CAMBIO 12 — Nuevo endpoint GET /api/v1/assets/popular
═══════════════════════════════════════════════════════════════════════════

En el controlador de assets añadir:

  @GetMapping("/popular")
  public Map<String, List<AssetOptionResponse>> getPopular() {
      return Map.of(
          "stocks",  assetSearchService.getPopular("stock"),
          "etfs",    assetSearchService.getPopular("etf"),
          "cryptos", assetSearchService.getPopular("crypto")
      );
  }

Este endpoint alimenta la sección "Populares" del modal en frontend.
No requiere autenticación (público).

═══════════════════════════════════════════════════════════════════════════
CAMBIO 13 — Resolución de asset_name en servicio de transacciones
═══════════════════════════════════════════════════════════════════════════

En el servicio que procesa BuyTransaction / SellTransaction:

  // Si el cliente no envió assetName, resolverlo desde catálogo Redis
  if (request.assetName() == null || request.assetName().isBlank()) {
      String resolved = assetSearchService.findNameBySymbol(request.assetSymbol())
          .orElse(null);
      // Usar resolved al construir la entidad Transaction
  }

═══════════════════════════════════════════════════════════════════════════
CAMBIO 14 — CacheConfig: añadir cache "asset-logos" con TTL 24h
═══════════════════════════════════════════════════════════════════════════

En shared/infrastructure/config/CacheConfig.java añadir:
  Nombre: "asset-logos"
  TTL: Duration.ofHours(24)

Seguir el mismo patrón de las entradas existentes.

═══════════════════════════════════════════════════════════════════════════
CAMBIO 15 — Habilitar @Scheduled en Spring Boot
═══════════════════════════════════════════════════════════════════════════

Verificar que exista @EnableScheduling en alguna clase @Configuration.
Si no existe, añadirlo en shared/infrastructure/configuration/SchedulingConfig.java:

  @Configuration
  @EnableScheduling
  public class SchedulingConfig {}

═══════════════════════════════════════════════════════════════════════════
VALIDACIÓN FINAL
═══════════════════════════════════════════════════════════════════════════

1. ./mvnw spotless:apply
2. ./mvnw clean package -DskipTests   → debe compilar sin errores
3. ./mvnw test                         → tests existentes deben pasar

Si hay tests de AssetSearchService que validan el catálogo hardcodeado
o el tamaño de CATALOG, actualizarlos para reflejar la nueva arquitectura
Redis (mockear CatalogRedisService en tests unitarios).

Commit:
  "feat(asset,transaction): dynamic Redis catalog, ETF/BOND logos via CompaniesLogo,
   dividend_detail table, new transaction fields (asset_name, exchange, broker, currency)"