package com.mx.cryptomonitor.user.application.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {
  private UUID id;
  private String name;
  private String code;
  private String description;
}
