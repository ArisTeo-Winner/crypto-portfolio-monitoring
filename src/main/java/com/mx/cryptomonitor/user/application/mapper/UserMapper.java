package com.mx.cryptomonitor.user.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mx.cryptomonitor.user.application.dto.UserDTO;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.domain.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

  // Mapeo de User a UserDTO
  UserDTO toDTO(User user);

  // Mapeo de UserDTO a User
  User toEntity(UserDTO userDTO);

  // Mapeo de UserRegistrationRequest a User
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "username", source = "username")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "lastLogin", ignore = true)
  @Mapping(target = "active", constant = "true")
  @Mapping(target = "passwordHash", source = "password")
  User toEntity(UserRegistrationRequest request);

  // Mapeo de User a UserResponse
  UserResponse toResponse(User user);
}
