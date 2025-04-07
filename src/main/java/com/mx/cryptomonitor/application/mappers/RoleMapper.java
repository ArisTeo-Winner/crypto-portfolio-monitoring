package com.mx.cryptomonitor.application.mappers;

import com.mx.cryptomonitor.application.dtos.RoleDTO;
import com.mx.cryptomonitor.domain.models.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleMapper {

    private final PermissionMapper permissionMapper;

    public RoleDTO toDTO(Role role) {
        if (role == null) {
            return null;
        }

        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());

        if (role.getPermissions() != null) {
            dto.setPermissions(role.getPermissions().stream()
                    .map(permissionMapper::toDTO)
                    .collect(Collectors.toSet()));
        }

        return dto;
    }

    public List<RoleDTO> toDTOList(List<Role> roles) {
        return roles.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Role toEntity(RoleDTO dto) {
        if (dto == null) {
            return null;
        }

        Role role = new Role();
        role.setId(dto.getId());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());

        return role;
    }
}