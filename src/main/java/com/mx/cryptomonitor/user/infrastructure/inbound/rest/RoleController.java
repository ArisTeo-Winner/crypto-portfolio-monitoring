package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.mx.cryptomonitor.user.application.dto.RoleDTO;
import com.mx.cryptomonitor.user.application.mapper.RoleMapper;
import com.mx.cryptomonitor.user.application.service.RoleService;
import com.mx.cryptomonitor.user.domain.model.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roleService;
  private final RoleMapper roleMapper;

  @Operation(summary = "Listar roles", description = "Obtiene todos los roles registrados")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Roles obtenidos correctamente"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @GetMapping
  @PreAuthorize("hasAuthority('USER_READ')")
  public ResponseEntity<List<RoleDTO>> getAllRoles() {
    List<Role> roles = roleService.getAllRoles();
    return ResponseEntity.ok(roleMapper.toDTOList(roles));
  }

  @Operation(summary = "Obtener rol por id", description = "Obtiene un rol por su identificador")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Rol obtenido correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_READ')")
  public ResponseEntity<RoleDTO> getRoleById(@PathVariable UUID id) {
    Role role = roleService.getRoleById(id);
    return ResponseEntity.ok(roleMapper.toDTO(role));
  }

  @Operation(summary = "Crear rol", description = "Crea un nuevo rol en el sistema")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Rol creado correctamente"),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @PostMapping
  @PreAuthorize("hasAuthority('USER_CREATE')")
  public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO) {
    Role role = roleMapper.toEntity(roleDTO);
    Role savedRole = roleService.createRole(role);
    return new ResponseEntity<>(roleMapper.toDTO(savedRole), HttpStatus.CREATED);
  }

  @Operation(
      summary = "Agregar permiso a rol",
      description = "Asigna un permiso existente a un rol existente")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Permiso agregado correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol o permiso no encontrado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @PutMapping("/{roleId}/permissions/{permissionId}")
  @PreAuthorize("hasAuthority('USER_UPDATE')")
  public ResponseEntity<RoleDTO> addPermissionToRole(
      @PathVariable UUID roleId, @PathVariable UUID permissionId) {
    Role updatedRole = roleService.addPermissionToRole(roleId, permissionId);
    return ResponseEntity.ok(roleMapper.toDTO(updatedRole));
  }

  @Operation(
      summary = "Eliminar permiso de rol",
      description = "Remueve una asignacion de permiso sobre un rol")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Permiso removido correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol o permiso no encontrado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @DeleteMapping("/{roleId}/permissions/{permissionId}")
  @PreAuthorize("hasAuthority('USER_UPDATE')")
  public ResponseEntity<RoleDTO> removePermissionFromRole(
      @PathVariable UUID roleId, @PathVariable UUID permissionId) {
    Role updatedRole = roleService.removePermissionFromRole(roleId, permissionId);
    return ResponseEntity.ok(roleMapper.toDTO(updatedRole));
  }

  @Operation(summary = "Eliminar rol", description = "Elimina un rol por su identificador")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Rol eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "No autorizado")
      })
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_DELETE')")
  public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
    roleService.deleteRole(id);
    return ResponseEntity.noContent().build();
  }
}
