package com.example.uart_blue;

public class PasswordManager {
    private String currentPassword = "000000"; // Initial password

    // Method to verify the password
    public boolean verifyPassword(String inputPassword) {
        return inputPassword.equals(currentPassword);
    }

    // Method to update the ID (returns true if password is correct)
    public boolean changeId(String newId, String inputPassword) {
        if (verifyPassword(inputPassword)) {
            // Logic to change the ID
            // e.g., Update UI, store new ID, etc.
            return true;
        }
        return false;
    }
}
