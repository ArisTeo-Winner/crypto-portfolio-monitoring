package com.mx.cryptomonitor.domain.models;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "sessions")
@Data
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Generador automático de UUID
    @Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;
	
	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
    @Column(name = "refresh_token_id")
    private UUID refreshTokenId;
	
	@Column(name = "login_time")
	private LocalDateTime loginTime;
	
	@Column(name = "logout_time")
	private LocalDateTime logoutTime;
	
	@Column(name = "is_active")
    private boolean active = true;

	
}
