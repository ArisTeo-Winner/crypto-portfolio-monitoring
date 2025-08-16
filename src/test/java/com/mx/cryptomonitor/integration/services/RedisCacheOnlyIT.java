package com.mx.cryptomonitor.integration.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.integration.support.TestProbeService;

@SpringBootTest
@ActiveProfiles("test")
class RedisCacheOnlyIT {

	@Autowired
	private TestProbeService probe;

	  @Test
	  void miss_then_hit_and_key_exists() {
	    var v1 = probe.expensive("foo");   // miss -> put
	    var v2 = probe.expensive("foo");   // hit
	    assertEquals(v1, v2);
	    assertEquals(1, probe.calls());
	  }

}
