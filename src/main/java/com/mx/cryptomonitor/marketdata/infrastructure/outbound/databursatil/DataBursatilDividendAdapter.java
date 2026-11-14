package com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderUnavailableException;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Proveedor de dividendos de la BMV (MXN nativo) vía DataBursatil {@code GET /v2/emisoras?letra=} —
 * el leg MXN del ADR-0016. Los dividendos vienen embebidos por emisora→serie en {@code dividendos}:
 * {@code reciente} (el más nuevo/próximo, con {@code pago}, {@code divisa}, {@code fechaexcupon} =
 * ex-date) e {@code historico} (mapa {@code fechaPago → {pago, tipo}}). Mapea a {@link
 * DividendEventData} en MXN; dedup por fecha de pago (un evento por pago, prefiriendo {@code
 * reciente} que trae ex-date y divisa).
 *
 * <p>Ruteo de símbolo: nuestro ticker canónico concatena emisora+serie ({@code BIMBOA}); se
 * consulta {@code letra=} con ese símbolo (sin {@code .MX}) y en la respuesta anidada se elige la
 * entrada cuya {@code emisora+serie} coincide (o la única si no hay match exacto). El símbolo
 * almacenado es el de ENTRADA, para que {@code dividend-growth} lo encuentre.
 *
 * <p>Si DataBursatil no está configurado o falla, devuelve lista vacía (la ingesta continúa). El
 * {@code token} viaja como query param pero la URL nunca se loggea (solo contexto/clase).
 */
@Component
@Slf4j
public class DataBursatilDividendAdapter implements DividendProviderPort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String CASH_DIVIDEND = "recurring";
  private static final ParameterizedTypeReference<Map<String, Object>> EMISORAS_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final String token;

  public DataBursatilDividendAdapter(
      @Qualifier("databursatilWebClient") WebClient webClient, DataBursatilProperties properties) {
    this.webClient = webClient;
    this.token = properties.token();
  }

  @Override
  public List<DividendEventData> fetchDividends(String ticker) {
    if (!StringUtils.hasText(token) || !StringUtils.hasText(ticker)) {
      return List.of();
    }
    String symbol = ticker.trim().toUpperCase(Locale.ROOT).replace(".MX", "").replace(" ", "");
    if (symbol.isEmpty()) {
      return List.of();
    }
    // 'letra' filtra por PREFIJO de la emisora y nuestro simbolo es emisora+serie (BIMBOA=BIMBO+A).
    // Se consulta con prefijos decrecientes hasta que la emisora aparece y se elige la serie cuya
    // emisora+serie coincide con el simbolo. Normalmente basta el primer intento (1 credito).
    for (int n = Math.min(4, symbol.length()); n >= 2; n--) {
      Map<String, Object> root = query(symbol.substring(0, n));
      if (root == null || root.containsKey("Error")) {
        continue; // prefijo mas largo que la emisora, o sin datos: probar prefijo menor
      }
      Map<String, Object> serie = selectSerie(root, symbol);
      if (serie != null) {
        return parseDividends(serie, symbol);
      }
      break; // la emisora aparece pero ninguna serie coincide: no insistir
    }
    return List.of();
  }

  private Map<String, Object> query(String letra) {
    try {
      return webClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/v2/emisoras")
                      .queryParam("letra", letra)
                      .queryParam("mercado", "local")
                      .queryParam("token", token)
                      .build())
          .retrieve()
          .bodyToMono(EMISORAS_TYPE)
          .timeout(TIMEOUT)
          .block();
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo obtener dividendos de DataBursatil (letra {}) ({}); no concluyente",
          letra,
          ex.getClass().getSimpleName());
      throw new DividendProviderUnavailableException(
          "DataBursatil " + letra + ": " + ex.getClass().getSimpleName());
    }
  }

  @SuppressWarnings("unchecked")
  List<DividendEventData> parseDividends(Map<String, Object> serie, String symbol) {
    if (serie == null || !(serie.get("dividendos") instanceof Map<?, ?> dividendos)) {
      return List.of();
    }
    // Dedup por fecha de pago: historico primero, reciente sobre-escribe (trae ex-date y divisa).
    Map<LocalDate, DividendEventData> byPayDate = new TreeMap<>();
    collect(
        byPayDate, (Map<String, Object>) ((Map<?, ?>) dividendos).get("historico"), symbol, false);
    collect(
        byPayDate, (Map<String, Object>) ((Map<?, ?>) dividendos).get("reciente"), symbol, true);
    return new ArrayList<>(byPayDate.values());
  }

  /** Elige la serie cuya emisora+serie coincide con el símbolo, o la única disponible. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> selectSerie(Map<String, Object> root, String symbol) {
    if (root == null) {
      return null;
    }
    Map<String, Object> onlyCandidate = null;
    int candidates = 0;
    for (Map.Entry<String, Object> emisoraEntry : root.entrySet()) {
      if (!(emisoraEntry.getValue() instanceof Map<?, ?> seriesMap)) {
        continue;
      }
      for (Map.Entry<?, ?> serieEntry : seriesMap.entrySet()) {
        if (!(serieEntry.getValue() instanceof Map<?, ?> payload)) {
          continue;
        }
        candidates++;
        onlyCandidate = (Map<String, Object>) payload;
        String concat =
            (emisoraEntry.getKey() + String.valueOf(serieEntry.getKey()))
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");
        if (concat.equals(symbol)) {
          return (Map<String, Object>) payload;
        }
      }
    }
    return candidates == 1 ? onlyCandidate : null;
  }

  private void collect(
      Map<LocalDate, DividendEventData> target,
      Map<String, Object> node,
      String symbol,
      boolean reciente) {
    if (node == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> row)) {
        continue;
      }
      LocalDate payDate = parseDate(entry.getKey());
      BigDecimal pago = toBigDecimal(row.get("pago"));
      if (payDate == null || pago == null) {
        continue;
      }
      LocalDate exDate = reciente ? parseDate(row.get("fechaexcupon")) : null;
      if (exDate == null) {
        exDate = payDate; // historico no trae ex-date: la fecha de pago es el proxy pivotal
      }
      String currency =
          row.get("divisa") == null ? "MXN" : String.valueOf(row.get("divisa")).trim();
      target.put(
          payDate,
          new DividendEventData(
              symbol,
              exDate,
              null,
              payDate,
              null,
              pago,
              StringUtils.hasText(currency) ? currency : "MXN",
              null,
              CASH_DIVIDEND,
              null,
              null,
              "databursatil:" + symbol + ":" + payDate));
    }
  }

  private LocalDate parseDate(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(raw);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
