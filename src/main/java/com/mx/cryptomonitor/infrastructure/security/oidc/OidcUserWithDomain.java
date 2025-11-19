package com.mx.cryptomonitor.infrastructure.security.oidc;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.mx.cryptomonitor.domain.models.User;

public record OidcUserWithDomain(
		User domainUser, 
		Collection<? extends GrantedAuthority> authorities,
		OidcIdToken idToken,
		OidcUserInfo userInfo) implements OidcUser {

	@Override
	public Map<String, Object> getAttributes() {
		// TODO Auto-generated method stub
		return userInfo.getClaims();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// TODO Auto-generated method stub
		return authorities;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return idToken.getSubject();
	}

	@Override
	public Map<String, Object> getClaims() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OidcUserInfo getUserInfo() {
		// TODO Auto-generated method stub
		return userInfo;
	}

	@Override
	public OidcIdToken getIdToken() {
		// TODO Auto-generated method stub
		return idToken;
	}

}
