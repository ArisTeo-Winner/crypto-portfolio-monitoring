package com.mx.cryptomonitor.shared.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita {@code @Async} y define el executor dedicado para tareas best-effort de catálogo (p.ej.
 * resolución de íconos on-demand). Cola acotada + DiscardPolicy: si el sistema está saturado se
 * descarta la tarea — el activo se catalogará en una transacción/lectura posterior; nunca bloquea
 * el hot path.
 */
@Configuration
// proxyTargetClass = true (CGLIB): CatalogSyncService implementa AssetCatalogRefreshPort y ademas
// tiene metodos @Scheduled/@Async que no estan en la interfaz; con proxy JDK (por interfaz) esos
// metodos no serian invocables. CGLIB proxya la clase concreta y los preserva.
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig {

  @Bean(name = "assetIconExecutor")
  public Executor assetIconExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("asset-icon-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    executor.initialize();
    return executor;
  }
}
