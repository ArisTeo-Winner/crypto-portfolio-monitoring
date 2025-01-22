package com.mx.cryptomonitor;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ListUsers {

	public static void main(String[] args) {
        // Configuración de conexión a la base de datos
        String url = "jdbc:postgresql://localhost:5432/crypto_portafolio";
        String user = "postgres"; // Cambia esto al usuario de tu base de datos
        String password = "Rammstein"; // Cambia esto a la contraseña de tu base de datos

        // Consulta SQL: Seleccionamos todos los campos relevantes de la tabla
        String query = """
                SELECT 
                    id, username, email, first_name, last_name, phone_number, 
                    address, city, state, postal_code, country, date_of_birth, 
                    active, created_at, updated_at, last_login 
                FROM users
                """;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            // Encabezado de la tabla
            System.out.printf("%-36s %-15s %-25s %-15s %-15s %-15s %-20s %-15s %-10s %-12s %-10s %-12s %-7s %-20s %-20s %-20s%n",
                    "ID", "Username", "Email", "First Name", "Last Name", "Phone", "Address", "City",
                    "State", "Postal", "Country", "Birth Date", "Active", "Created At", "Updated At", "Last Login");
            System.out.println("=".repeat(200));

            // Mostrar resultados de la consulta
            while (rs.next()) {
                String id = rs.getString("id");
                String username = rs.getString("username");
                String email = rs.getString("email");
                String firstName = rs.getString("first_name");
                String lastName = rs.getString("last_name");
                String phoneNumber = rs.getString("phone_number");
                String address = rs.getString("address");
                String city = rs.getString("city");
                String state = rs.getString("state");
                String postalCode = rs.getString("postal_code");
                String country = rs.getString("country");
                String birthDate = rs.getString("date_of_birth");
                boolean active = rs.getBoolean("active");
                String createdAt = rs.getString("created_at");
                String updatedAt = rs.getString("updated_at");
                String lastLogin = rs.getString("last_login");

                // Mostrar cada fila con formato
                System.out.printf("%-36s %-15s %-25s %-15s %-15s %-15s %-20s %-15s %-10s %-12s %-10s %-12s %-7s %-20s %-20s %-20s%n",
                        id, username, email, firstName, lastName, phoneNumber, address, city, state,
                        postalCode, country, birthDate, active, createdAt, updatedAt, lastLogin);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
