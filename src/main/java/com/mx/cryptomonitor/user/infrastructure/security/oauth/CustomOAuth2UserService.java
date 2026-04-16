package com.mx.cryptomonitor.user.infrastructure.security.oauth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.user.domain.model.AuthProvider;
import com.mx.cryptomonitor.user.domain.model.Permission;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.model.UserAuthProvider;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;
import com.mx.cryptomonitor.user.domain.repository.UserAuthProviderRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;

  private static final String DEFAULT_ROLE = "ROLE_USER";

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

    OAuth2User oauthUsr = super.loadUser(userRequest);

    Map<String, Object> attr = oauthUsr.getAttributes();

    String sub = asString(attr.get("sub"));
    String email = asString(attr.get("email"));
    String givenName = asString(attr.get("given_name"));
    String familyName = asString(attr.get("family_name"));
    Boolean emailVerified = asBool(attr.get("email_verified"));
    String pictureUrl = asString(attr.get("picture"));

    if (sub == null || email == null) {
      log.warn("OAuth2 login sin sub o email: sub={}, email={}", sub, email);
      throw new OAuth2AuthenticationException(
          "Falta claims obligatorios (sub/email) en respuesta de Google");
    }

    Optional<UserAuthProvider> linkOpt =
        userAuthProviderRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, sub);

    User user;
    if (linkOpt.isPresent()) {
      user = linkOpt.get().getUser();
    } else {
      user = userRepository.findByEmailIgnoreCase(email).orElse(null);
      if (user == null) {
        user = new User();
        user.setEmail(email);
        user.setUsername(safeUsernameFromEmail(email));
        user.setFirstName(givenName);
        user.setLastName(familyName);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        user.setPasswordHash(null);
        user = userRepository.save(user);

        Role defaultRole =
            roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("No existe el rol " + DEFAULT_ROLE));

        user.addRole(defaultRole);
        userRepository.save(user);

        userAuthProviderRepository.save(
            UserAuthProvider.builder()
                .user(user)
                .authProvider(AuthProvider.GOOGLE)
                .providerId(sub)
                .providerEmail(email)
                .createdAt(LocalDateTime.now())
                .build());

      } else {
        if (!userAuthProviderRepository.existsByUserIdAndAuthProvider(
            user.getId(), AuthProvider.GOOGLE)) {
          userAuthProviderRepository.save(
              UserAuthProvider.builder()
                  .user(user)
                  .authProvider(AuthProvider.GOOGLE)
                  .providerId(sub)
                  .providerEmail(email)
                  .createdAt(LocalDateTime.now())
                  .build());
        }

        if (givenName != null) {
          user.setFirstName(givenName);
        }
        if (familyName != null) {
          user.setLastName(familyName);
        }
        user.setLastLogin(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
      }
    }

    user.setLastLogin(LocalDateTime.now());
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);

    // Collection<? extends GrantedAuthority> authorities =
    //   user.getRoles().stream().map(r -> new SimpleGrantedAuthority(r.getName())).toList();

    List<GrantedAuthority> authorities = new ArrayList<>();
    for (Role role : user.getRoles()) {
      authorities.add(new SimpleGrantedAuthority(role.getName()));
      for (Permission permission : role.getPermissions()) {
        authorities.add(new SimpleGrantedAuthority(permission.getCode()));
      }
    }

    return new AuthenticatedUserPrincipal(user, attr, authorities);
  }

  private static String safeUsernameFromEmail(String email) {
    try {
      String base = email.split("@")[0];
      return base.length() > 32 ? base.substring(0, 32) : base;
    } catch (Exception e) {
      // TODO: handle exception
      return UUID.randomUUID().toString().substring(0, 8);
    }
  }

  private static String asString(Object o) {
    return o == null ? null : String.valueOf(o);
  }

  private static Boolean asBool(Object o) {
    return o == null ? null : Boolean.valueOf(String.valueOf(o));
  }
}
