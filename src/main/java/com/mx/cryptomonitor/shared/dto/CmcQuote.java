package com.mx.cryptomonitor.shared.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CmcQuote(BigDecimal price) {}
