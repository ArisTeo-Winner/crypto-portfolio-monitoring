package com.mx.cryptomonitor.shared.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDTO(
	    String error,
	    String message,
	    List<String> details,
	    LocalDateTime timestamp
	) {
	    public ErrorResponseDTO(String error, String message, List<String> details) {
	        this(error, message, details, LocalDateTime.now());
	    }
	}