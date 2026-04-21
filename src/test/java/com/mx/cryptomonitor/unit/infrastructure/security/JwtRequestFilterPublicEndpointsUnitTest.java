package com.mx.cryptomonitor.unit.infrastructure.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;

import jakarta.servlet.FilterChain;

class JwtRequestFilterPublicEndpointsUnitTest {

  @Test
  void publicEndpointPatternMustBypassJwtProcessing_marketdataWildcard() throws Exception {
    JwtRequestFilter filter = new JwtRequestFilter();

    UserRepository userRepository = mock(UserRepository.class);
    UserDetailsService userDetailsService = mock(UserDetailsService.class);
    JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
    SessionRepository sessionRepository = mock(SessionRepository.class);

    ReflectionTestUtils.setField(filter, "userRepository", userRepository);
    ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    ReflectionTestUtils.setField(filter, "jwtTokenUtil", jwtTokenUtil);
    ReflectionTestUtils.setField(filter, "sessionRepository", sessionRepository);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/marketdata/stock");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    verifyNoInteractions(userRepository, userDetailsService, jwtTokenUtil, sessionRepository);
  }

  @Test
  void publicEndpointMustBypassJwtProcessing_usersPublicTestGet() throws Exception {
    JwtRequestFilter filter = new JwtRequestFilter();

    UserRepository userRepository = mock(UserRepository.class);
    UserDetailsService userDetailsService = mock(UserDetailsService.class);
    JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
    SessionRepository sessionRepository = mock(SessionRepository.class);

    ReflectionTestUtils.setField(filter, "userRepository", userRepository);
    ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    ReflectionTestUtils.setField(filter, "jwtTokenUtil", jwtTokenUtil);
    ReflectionTestUtils.setField(filter, "sessionRepository", sessionRepository);

    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/users/public/test-get");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    verifyNoInteractions(userRepository, userDetailsService, jwtTokenUtil, sessionRepository);
  }

  @Test
  void publicEndpointPatternMustBypassJwtProcessing_cryptoWildcard() throws Exception {
    JwtRequestFilter filter = new JwtRequestFilter();

    UserRepository userRepository = mock(UserRepository.class);
    UserDetailsService userDetailsService = mock(UserDetailsService.class);
    JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
    SessionRepository sessionRepository = mock(SessionRepository.class);

    ReflectionTestUtils.setField(filter, "userRepository", userRepository);
    ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    ReflectionTestUtils.setField(filter, "jwtTokenUtil", jwtTokenUtil);
    ReflectionTestUtils.setField(filter, "sessionRepository", sessionRepository);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/crypto/BTC/price");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    verifyNoInteractions(userRepository, userDetailsService, jwtTokenUtil, sessionRepository);
  }
}
