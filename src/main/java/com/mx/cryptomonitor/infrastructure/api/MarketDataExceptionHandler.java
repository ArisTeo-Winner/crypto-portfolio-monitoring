package com.mx.cryptomonitor.infrastructure.api;

import com.mx.cryptomonitor.application.controllers.StockDataController;
import com.mx.cryptomonitor.infrastructure.exceptions.ExternalApiException;
import com.mx.cryptomonitor.infrastructure.exceptions.MarketDataException;
import com.mx.cryptomonitor.shared.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;


@RestControllerAdvice(assignableTypes = {StockDataController.class})
public class MarketDataExceptionHandler {


    private static final Logger log = LoggerFactory.getLogger(MarketDataExceptionHandler.class);

    /**
     * Maneja errores de validación en los DTO (por ejemplo, con @Valid).
     * Se dispara MethodArgumentNotValidException cuando Bean Validation falla.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        // Extraer mensajes de error de cada campo que falló la validación
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField().concat(": ").concat(err.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Bad Request",
                "Error de validación en la solicitud",
                details
        );

        log.error("Validation error: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja incompatibilidades de tipo (por ejemplo, se espera un número y llega un texto).
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatchException(
            TypeMismatchException ex,
            HttpServletRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Bad Request",
                "Error de tipo en el parámetro",
                List.of(ex.getMessage())
        );

        log.error("Type mismatch error: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja errores de parámetros inválidos (por ejemplo, símbolos vacíos o nulos).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Bad Request",
                ex.getMessage(),
                List.of("Parámetro inválido")
        );

        log.error("IllegalArgumentException: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja errores al comunicarse con la API externa (CoinMarketCap, Alpha Vantage, etc.).
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalApiException(
            ExternalApiException ex,
            HttpServletRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Bad Gateway",
                ex.getMessage(),
                List.of("Falla en la comunicación con la API externa")
        );

        log.error("ExternalApiException: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    /**
     * Maneja otros errores internos en MarketData (por ejemplo, lógica de parseo o cálculo).
     */
    @ExceptionHandler(MarketDataException.class)
    public ResponseEntity<ErrorResponseDTO> handleMarketDataException(
            MarketDataException ex,
            HttpServletRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Internal Server Error",
                ex.getMessage(),
                List.of("Error interno en MarketDataService")
        );

        log.error("MarketDataException: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Maneja cualquier otra excepción no contemplada en los handlers anteriores.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneralException(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "Internal Server Error",
                "Ocurrió un error inesperado",
                List.of(ex.getMessage())
        );

        log.error("Unhandled exception: {}", errorResponse, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
