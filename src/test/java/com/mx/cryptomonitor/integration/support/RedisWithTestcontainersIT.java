package com.mx.cryptomonitor.integration.support;

import static org.junit.jupiter.api.Assertions.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class RedisWithTestcontainersIT {

  private final Logger logger = LoggerFactory.getLogger(RedisWithTestcontainersIT.class);
  /*

      @Container
      static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

      @DynamicPropertySource
      static void redisProps(DynamicPropertyRegistry registry) {
          registry.add("spring.data.redis.host", redis::getHost);
          registry.add("spring.data.redis.port", redis::getFirstMappedPort);
      }

      @Autowired
      RedisConnectionFactory redisConnectionFactory;

      @Test
      void redisShouldReplyPong() {

  		logger.info("=== Ejecutando método redisShouldReplyPong() ===");


          var conn = redisConnectionFactory.getConnection();
          assertThat(conn.ping()).isEqualTo("PONG");
          conn.close();
      }
  */

}
