package com.mx.cryptomonitor.shared.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponse(
	    String username,
	    String email,
	    String firstName,
	    String lastName,
	    String phoneNumber,
	    String address,
	    String city,
	    String state,
	    String postalCode,
	    String country,
	    LocalDate dateOfBirth,
	    boolean active,
	    LocalDateTime createdAt) {

}
