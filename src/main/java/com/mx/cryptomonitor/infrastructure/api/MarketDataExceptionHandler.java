package com.mx.cryptomonitor.infrastructure.api;

import com.mx.cryptomonitor.application.controllers.StockDataController;
import com.mx.cryptomonitor.infrastructure.exceptions.ExternalApiException;
import com.mx.cryptomonitor.infrastructure.exceptions.MarketDataException;
import com.mx.cryptomonitor.shared.dto.response.ErrorResponseDTO;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;


import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
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
        		LocalDateTime.now(), 
        		HttpStatus.BAD_REQUEST.value(), 
        		"Error de validación en la solicitud", 
        		 details, 
        		 request.getRequestURI());

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
        		LocalDateTime.now(), 
        		HttpStatus.BAD_REQUEST.value(), 
                "Error de tipo en el parámetro",
                List.of(ex.getMessage()),
                request.getRequestURI()
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
                		LocalDateTime.now(), 
                		HttpStatus.BAD_REQUEST.value(), 
                		HttpStatus.BAD_REQUEST.getReasonPhrase(), 
                		List.of(ex.getMessage()), 
                		request.getRequestURI());
                
        log.error("IllegalArgumentException: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja errores al comunicarse con la API externa (CoinMarketCap, Alpha Vantage, etc.).
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalApiException(
            ExternalApiException ex,
            WebRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
        		LocalDateTime.now(), 
        		HttpStatus.BAD_REQUEST.value(),         		
                ex.getMessage(),
                List.of("Falla en la comunicación con la API externa"),
                request.getDescription(false)

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
            WebRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
        		LocalDateTime.now(),
        		HttpStatus.BAD_REQUEST.value(),         		
                ex.getMessage(),
                List.of("Error interno en MarketDataService"),
                request.getDescription(false)
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
            WebRequest request
    ) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
        		LocalDateTime.now(),
        		HttpStatus.BAD_REQUEST.value(),         		
                ex.getMessage(),
                List.of("Ocurrió un error inesperado"),
                request.getDescription(false)
        );

        log.error("Unhandled exception: {}", errorResponse, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    
    
    public class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
    
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ErrorResponseDTO> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        return buildErrorResponse("Invalid or tampered JWT signature", HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponseDTO> handleExpiredJwtException(ExpiredJwtException ex, HttpServletRequest request) {
        return buildErrorResponse("Expired JWT token", HttpStatus.UNAUTHORIZED, request);
    }
    
    // Helper method to build standard error response
    private ResponseEntity<ErrorResponseDTO> buildErrorResponse(String message, HttpStatus status, HttpServletRequest request) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                List.of(message),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, status);
    }
    
}
