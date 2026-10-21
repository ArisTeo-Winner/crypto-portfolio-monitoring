package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.MergerActionDetailEntity;

/** Acceso al detalle del subtipo MERGER (ADR-0014 F6b), llaveado por corporate_action_id. */
public interface MergerActionDetailRepository
    extends JpaRepository<MergerActionDetailEntity, UUID> {}
