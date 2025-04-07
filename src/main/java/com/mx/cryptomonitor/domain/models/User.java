package com.mx.cryptomonitor.domain.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Username is mandatory")
    private String username;

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is mandatory")
    private String email;

    @NotBlank(message = "Password is mandatory")
    private String passwordHash;

    @JsonProperty("firstName")
    @Column(name = "first_name")
    private String firstName;

    @JsonProperty("lastName")
    @Column(name = "last_name")
    private String lastName;

    @JsonProperty("phoneNumber")
    @Column(name = "phone_number")
    private String phoneNumber;
    private String address;
    private String city;

    @JsonProperty("state")
    private String state;

    @JsonProperty("postalCode")
    @Column(name = "postal_code")
    private String postalCode;
    private String country;
    private LocalDate dateOfBirth;
    private byte[] profilePicture;
    private String bio;

    private boolean active = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    private LocalDateTime lastLogin;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private List<Role> roles = new ArrayList<>();

    public User(String string, String string2) {
        // TODO Auto-generated constructor stub
    }

    /**
     * Agrega un rol al usuario si no lo tiene ya
     * 
     * @param role El rol a agregar
     * @return true si se agregó el rol, false si ya lo tenía
     */
    public boolean addRole(Role role) {
        if (roles == null) {
            roles = new ArrayList<>();
        }
        if (!roles.contains(role)) {
            roles.add(role);
            return true;
        }
        return false;
    }

    /**
     * Elimina un rol del usuario
     * 
     * @param role El rol a eliminar
     * @return true si se eliminó el rol, false si no lo tenía
     */
    public boolean removeRole(Role role) {
        if (roles != null) {
            return roles.remove(role);
        }
        return false;
    }
}
