package com.mx.cryptomonitor.user.application.dto;

import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
  private UUID id;
  private String name;
  private String description;
  private Set<PermissionDTO> permissions;
}
