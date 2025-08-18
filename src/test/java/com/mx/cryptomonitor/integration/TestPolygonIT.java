package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mx.cryptomonitor.infrastructure.configuration.EnvConfig;
import io.polygon.kotlin.sdk.rest.PolygonRestClient;

@SpringBootTest
public class TestPolygonIT {

    private final Logger logger = LoggerFactory.getLogger(TestPolygonIT.class);

    @Autowired
    private EnvConfig envConfig;

    
    @Test
    public void testPolygonApiKeyInjection() {

        logger.info("=== Ejecutando método testPolygonApiKeyInjection() desde TestPolygonIT ===");
        String msgAssets= envConfig.getMsgAssets();        
        logger.info("=== MSG de Assets: {} ===",msgAssets);

/*        String datasourceUrl = polygonConfig.getDataSourceUrl();        
        String posgresqlUrl = polygonConfig.getPosgresqlUrl();        
        String sourceUsername = polygonConfig.getDataSourceUsername();        
        String sourcePassord = polygonConfig.getDataSourcePassord();
                String polygonKey = polygonConfig.getMsgAssets();

        logger.info("=== KEY de Polygon : {} ===", polygonKey);
        logger.info("=== URL de DB : {} ===", datasourceUrl);
        logger.info("=== spring.datasource.url: {} ===",posgresqlUrl);
        logger.info("=== spring.datasource.username: {}",sourceUsername);
        logger.info("=== spring.datasource.password: {}",sourcePassord);
        
        // assertNotNull(polygonKey, "⚠️ La clave API de Polygon no debe ser null");
        // assertFalse(polygonKey.isEmpty(), "⚠️ La clave API de Polygon no debe estar
        // vacía");

        // PolygonRestClient client = new PolygonRestClient(polygonKey);
        // assertNotNull(client, "⚠️ El cliente de Polygon no debe ser null");
   */ 
        }
}
