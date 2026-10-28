package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.SpinoffActionDetailEntity;

/** Acceso al detalle del subtipo SPINOFF (ADR-0014 F6e), llaveado por corporate_action_id. */
public interface SpinoffActionDetailRepository
    extends JpaRepository<SpinoffActionDetailEntity, UUID> {}
