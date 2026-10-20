package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;

/** Acceso al detalle del subtipo DIVIDEND (ADR-0014 F3a), llaveado por corporate_action_id. */
public interface DividendActionDetailRepository
    extends JpaRepository<DividendActionDetailEntity, UUID> {}
