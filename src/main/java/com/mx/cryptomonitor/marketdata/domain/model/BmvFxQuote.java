package com.mx.cryptomonitor.marketdata.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BmvFxQuote(double u, double c, double m, String t) {}
