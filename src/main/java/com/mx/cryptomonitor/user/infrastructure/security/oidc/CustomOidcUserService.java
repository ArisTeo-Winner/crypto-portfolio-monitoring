package com.mx.cryptomonitor.user.infrastructure.security.oidc;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.user.domain.model.AuthProvider;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.model.UserAuthProvider;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserAuthProviderRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;

  private static final String DEFAULT_ROLE = "ROLE_USER";

  @Override
  @Transactional
  public OidcUser loadUser(OidcUserRequest userRequest) {
    // TODO Auto-generated method stub

    OidcUser oidc = super.loadUser(userRequest);

    String sub = oidc.getSubject();
    String email = oidc.getEmail();
    String givenName = (String) oidc.getClaims().getOrDefault("given_name", null);
    String familyName = (String) oidc.getClaims().getOrDefault("family_name", null);

    if (sub == null) {
      throw new IllegalStateException("OIDC sub nulo");
    }

    Optional<UserAuthProvider> existingLink =
        userAuthProviderRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, sub);
    User user;

    if (existingLink.isPresent()) {
      user = existingLink.get().getUser();
      existingLink.get().setLastLogin(LocalDateTime.now());
    } else {

      Optional<User> byEmail =
          (email != null) ? userRepository.findByEmailIgnoreCase(email) : Optional.empty();

      if (byEmail.isPresent()) {
        user = byEmail.get();
      } else {
        user = new User();
        user.setEmail(email);
        user.setUsername(buildUsername(email, sub));
        user.setFirstName(givenName);
        user.setLastName(familyName);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        user = userRepository.save(user);

        Role r =
            roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Falta rol " + DEFAULT_ROLE));
        user.addRole(r);
        userRepository.save(user);
      }

      UserAuthProvider uap =
          UserAuthProvider.builder()
              .user(user)
              .authProvider(AuthProvider.GOOGLE)
              .providerId(sub)
              .providerEmail(email)
              .createdAt(LocalDateTime.now())
              .lastLogin(LocalDateTime.now())
              .build();

      userAuthProviderRepository.save(uap);
    }

    Set<GrantedAuthority> authorities = new HashSet<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
    if (user.getRoles() != null) {
      authorities.addAll(
          user.getRoles().stream()
              .map(r -> new SimpleGrantedAuthority(r.getName()))
              .collect(Collectors.toSet()));
    }

    DefaultOidcUser delegate =
        new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo());

    return new OidcUserWithDomain(user, authorities, oidc.getIdToken(), oidc.getUserInfo());
  }

  private static String buildUsername(String email, String sub) {
    if (email != null && email.contains("@")) {
      String base = email.split("@")[0];
      return base.length() > 32 ? base.substring(0, 32) : base;
    }
    String gen = "g_" + sub;
    return gen.substring(0, Math.min(gen.length(), 32));
  }

  @Getter
  public static class DomainOidcUser implements OidcUser {
    private final OidcUser delegate;
    private final User domainUser;

    public DomainOidcUser(OidcUser delegate, User domainUser) {
      this.delegate = delegate;
      this.domainUser = domainUser;
    }

    @Override
    public Map<String, Object> getAttributes() {
      // TODO Auto-generated method stub
      return delegate.getAttributes();
    }

    @Override
    public String getName() {
      // TODO Auto-generated method stub
      return delegate.getName();
    }

    @Override
    public Map<String, Object> getClaims() {
      // TODO Auto-generated method stub
      return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
      // TODO Auto-generated method stub
      return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
      // TODO Auto-generated method stub
      return delegate.getIdToken();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      // TODO Auto-generated method stub
      return delegate.getAuthorities();
    }
  }
}
