package com.mx.cryptomonitor.user.application.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.user.application.dto.PermissionDTO;
import com.mx.cryptomonitor.user.domain.model.Permission;

@Component
public class PermissionMapper {

  public PermissionDTO toDTO(Permission permission) {
    if (permission == null) {
      return null;
    }

    PermissionDTO dto = new PermissionDTO();
    dto.setId(permission.getId());
    dto.setName(permission.getName());
    dto.setCode(permission.getCode());
    dto.setDescription(permission.getDescription());

    return dto;
  }

  public List<PermissionDTO> toDTOList(List<Permission> permissions) {
    return permissions.stream().map(this::toDTO).collect(Collectors.toList());
  }

  public Permission toEntity(PermissionDTO dto) {
    if (dto == null) {
      return null;
    }

    Permission permission = new Permission();
    permission.setId(dto.getId());
    permission.setName(dto.getName());
    permission.setCode(dto.getCode());
    permission.setDescription(dto.getDescription());

    return permission;
  }
}
