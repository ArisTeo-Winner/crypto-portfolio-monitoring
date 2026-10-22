package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.BankruptcyActionDetailEntity;

/** Acceso al detalle del subtipo BANKRUPTCY (ADR-0014 F6d), llaveado por corporate_action_id. */
public interface BankruptcyActionDetailRepository
    extends JpaRepository<BankruptcyActionDetailEntity, UUID> {}
