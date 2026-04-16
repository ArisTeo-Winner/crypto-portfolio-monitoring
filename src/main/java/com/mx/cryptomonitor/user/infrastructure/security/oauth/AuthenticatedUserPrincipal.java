package com.mx.cryptomonitor.user.infrastructure.security.oauth;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.mx.cryptomonitor.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthenticatedUserPrincipal implements OAuth2User, UserDetails {

  private final User domainUser;
  private final Map<String, Object> attributes;
  private final Collection<? extends GrantedAuthority> authorities;

  public User getDomainUser() {
    return domainUser;
  }

  public UUID getUserId() {
    return domainUser.getId();
  }

  @Override
  public Map<String, Object> getAttributes() {
    // TODO Auto-generated method stub
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // TODO Auto-generated method stub
    return authorities;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    Object sub = attributes.get("sub");
    return sub != null ? sub.toString() : domainUser.getId().toString();
  }

  @Override
  public String getPassword() {
    // TODO Auto-generated method stub
    String ph = domainUser.getPasswordHash();
    return (ph == null || ph.isBlank()) ? "{noop}N/A" : ph;
  }

  @Override
  public String getUsername() {
    // TODO Auto-generated method stub
    return domainUser.getEmail();
  }
}
