package com.mx.cryptomonitor.marketdata.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BmvQuote(
    double u,
    double p,
    double a,
    double x,
    double n,
    double c,
    double m,
    double v,
    double o,
    double i,
    String f) {}
