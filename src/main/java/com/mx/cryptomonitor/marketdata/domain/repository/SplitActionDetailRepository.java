package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.SplitActionDetailEntity;

/** Acceso al detalle del subtipo SPLIT (ADR-0014 F2b), llaveado por corporate_action_id. */
public interface SplitActionDetailRepository extends JpaRepository<SplitActionDetailEntity, UUID> {}
