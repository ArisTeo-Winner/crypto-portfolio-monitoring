package com.mx.cryptomonitor.domain.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    @GeneratedValue(strategy = GenerationType.AUTO)
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
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime lastLogin;
    
	   public User(String string, String string2) {
		// TODO Auto-generated constructor stub
	}
}
