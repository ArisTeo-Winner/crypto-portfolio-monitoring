package com.mx.cryptomonitor.shared.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDTO(

	     LocalDateTime localDateTime,
	     int status,
	     String error,
	     List<String> errors,
	     String path
	) {
	
	    
	}