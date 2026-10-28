package com.mx.cryptomonitor.portfolio.application.port.in;

import com.mx.cryptomonitor.portfolio.application.dto.request.SplitPreviewRequest;
import com.mx.cryptomonitor.portfolio.application.dto.response.SplitPreviewResponse;

/**
 * Calcula el preview de ajuste por split para una operacion en captura (ADR-0011), sin persistir.
 */
public interface GetSplitPreviewUseCase {

  SplitPreviewResponse preview(SplitPreviewRequest request);
}
