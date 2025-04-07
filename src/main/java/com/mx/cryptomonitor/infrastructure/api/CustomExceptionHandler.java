package com.mx.cryptomonitor.infrastructure.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.application.controllers.UserController;
import com.mx.cryptomonitor.shared.dto.ErrorResponseDTO;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice(assignableTypes = {UserController.class})
public class CustomExceptionHandler {


    private static final Logger logger = LoggerFactory.getLogger(CustomExceptionHandler.class);

    // Manejo de errores de validación
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errors(errors)
                .path(request.getRequestURI())
                .build();

        logger.error("Validation error: {}", errorResponse);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    // Manejo de ConstraintViolationException (errores en anotaciones de validación)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());

        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errors(errors)
                .path(request.getRequestURI())
                .build();

        logger.error("Constraint violation error: {}", errorResponse);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    // Captura de excepciones genéricas
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex, HttpServletRequest request) {

        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .errors(List.of(ex.getMessage()))
                .path(request.getRequestURI())
                .build();

        logger.error("Unhandled exception: {}", errorResponse);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponseDTO> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
        		.timestamp(LocalDateTime.now())
        		.status(HttpStatus.UNAUTHORIZED.value())
        		.error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
        		.errors(List.of(ex.getMessage()))
        		.path(request.getRequestURI())
        		.build();
        
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
        		.timestamp(LocalDateTime.now())
        		.status(HttpStatus.BAD_REQUEST.value())
        		.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
        		.errors(List.of(ex.getMessage()))
        		.path(request.getRequestURI())
        		.build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ErrorResponseDTO> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
        		.timestamp(LocalDateTime.now())
        		.status(HttpStatus.BAD_REQUEST.value())
        		.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
        		.errors(List.of(ex.getMessage(), "Invalid or tampered JWT signature"))
        		.path(request.getRequestURI())
        		.build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        
    }
    
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponseDTO> handleExpiredJwtException(ExpiredJwtException ex, HttpServletRequest request){
        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
        		.timestamp(LocalDateTime.now())
        		.status(HttpStatus.UNAUTHORIZED.value())
        		.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
        		.errors(List.of(ex.getMessage(),"Expired JWT token"))
        		.path(request.getRequestURI())
        		.build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);    
        }
    
 
}
