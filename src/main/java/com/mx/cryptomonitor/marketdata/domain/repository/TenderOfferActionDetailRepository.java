package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.TenderOfferActionDetailEntity;

/** Acceso al detalle del subtipo TENDER_OFFER (ADR-0014 F6c), llaveado por corporate_action_id. */
public interface TenderOfferActionDetailRepository
    extends JpaRepository<TenderOfferActionDetailEntity, UUID> {}
