package com.mx.cryptomonitor.infrastructure.security.oauth;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.mx.cryptomonitor.domain.models.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthenticatedUserPrincipal implements OAuth2User{
	
	private final User domainUser;
	private final Map<String, Object> attributes;
	private final Collection<? extends GrantedAuthority> authorities;

	public User getDomainUser() {
		return domainUser;
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

}
