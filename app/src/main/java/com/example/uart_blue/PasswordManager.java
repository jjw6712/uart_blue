package com.example.uart_blue;

public class PasswordManager {
    private String currentPassword = "000000"; // Initial password

    // Method to verify the password
    public boolean verifyPassword(String inputPassword) {
        return inputPassword.equals(currentPassword);
    }

    // Method to change the password (returns true if the old password is correct and the password was successfully changed)
    public boolean changePassword(String oldPassword, String newPassword) {
        if (verifyPassword(oldPassword)) {
            currentPassword = newPassword; // Update the password
            return true;
        }
        return false;
    }
}

