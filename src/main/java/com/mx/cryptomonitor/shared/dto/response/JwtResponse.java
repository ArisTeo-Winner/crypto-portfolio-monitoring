package com.mx.cryptomonitor.shared.dto.response;

public record JwtResponse(
		String accessToken, 
		String refreshToken) {

}
