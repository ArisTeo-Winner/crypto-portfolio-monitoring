package com.mx.cryptomonitor.shared.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRegistrationRequest(
		
	    @NotBlank(message = "Username is mandatory")
	    String username,
	    
	    @Email(message = "Email should be valid")
	    @NotBlank(message = "Email is mandatory")
	    String email,
	    
	    @NotBlank(message = "Password is mandatory")
	    String password,
	    String firstName,
	    String lastName,
	    String phoneNumber,
	    String address,
	    String city,
	    String state,
	    String postalCode,
	    String country,
	    LocalDate dateOfBirth) {

}
