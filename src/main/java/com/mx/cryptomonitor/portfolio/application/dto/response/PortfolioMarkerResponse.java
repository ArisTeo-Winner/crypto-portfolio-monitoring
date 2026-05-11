package com.mx.cryptomonitor.portfolio.application.dto.response;

public record PortfolioMarkerResponse(
    long time, String position, String color, String shape, String text) {}
