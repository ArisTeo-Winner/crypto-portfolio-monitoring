package com.mx.cryptomonitor.application.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mx.cryptomonitor.application.dtos.UserDTO;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.shared.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.shared.dto.response.UserResponse;

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
