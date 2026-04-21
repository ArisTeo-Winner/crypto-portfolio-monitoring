package com.mx.cryptomonitor.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.user.application.dto.RoleDTO;
import com.mx.cryptomonitor.user.application.mapper.RoleMapper;
import com.mx.cryptomonitor.user.application.service.RoleService;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.RoleController;
import com.mx.cryptomonitor.user.infrastructure.security.JwtRequestFilter;

import lombok.extern.slf4j.Slf4j;

@WebMvcTest(RoleController.class)
@AutoConfigureMockMvc(addFilters = false) // ✅ Desactivar filtros de seguridad en la prueba
@Slf4j
class RoleControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private RoleService roleService;

  @MockBean private RoleMapper roleMapper;

  @MockBean private JwtRequestFilter jwtAuthFilter; // Si usas seguridad

  @Test
  @WithMockUser(roles = {"ADMIN"})
  void shouldGetAllRoles() throws Exception {
    // Arrange

    UUID rolId = UUID.randomUUID();

    List<Role> roles = List.of(new Role(rolId, "ROLE_ADMIN", "Admin Role", Set.of()));
    List<RoleDTO> roleDTOs = List.of(new RoleDTO(rolId, "ROLE_ADMIN", "Admin Role", Set.of()));

    var set = new HashSet<>(roles);

    log.info("Respuesta roleDTOs: {}", set.toString());

    var setDTOs = new HashSet<>(roleDTOs);

    log.info("Listar RoleDTO: {}", setDTOs.toString());

    when(roleService.getAllRoles()).thenReturn(roles);
    when(roleMapper.toDTOList(roles)).thenReturn(roleDTOs);

    // Act & Assert
    mockMvc
        .perform(get("/api/v1/roles").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("ROLE_ADMIN"))
        .andExpect(jsonPath("$[0].description").value("Admin Role"))
        .andDo(print());
  }
}
