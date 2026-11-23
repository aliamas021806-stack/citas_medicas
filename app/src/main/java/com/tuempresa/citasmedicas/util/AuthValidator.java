package com.tuempresa.citasmedicas.util;

import java.util.regex.Pattern;

/**
 * Reglas de validación de autenticación.
 * <p>
 * Clase SIN dependencias de Android a propósito: así puede probarse en una JVM
 * normal (JUnit) sin Robolectric ni emulador.
 */
public final class AuthValidator {

    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MIN_AGE = 1;
    public static final int MAX_AGE = 120;
    public static final int MIN_NAME_LENGTH = 3;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private AuthValidator() {
        // Clase de utilidad: no se instancia.
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    public static boolean isValidAge(int age) {
        return age >= MIN_AGE && age <= MAX_AGE;
    }

    /**
     * Valida el login.
     *
     * @return {@code null} si es válido, o el mensaje de error en español.
     */
    public static String validateLogin(String email, String password) {
        if (!isValidEmail(email)) {
            return "Ingresa un correo electrónico válido";
        }
        if (!isValidPassword(password)) {
            return "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres";
        }
        return null;
    }

    /**
     * Valida el registro.
     *
     * @return {@code null} si es válido, o el mensaje de error en español.
     */
    public static String validateRegister(String fullName, String email, String password, int age) {
        if (isBlank(fullName) || fullName.trim().length() < MIN_NAME_LENGTH) {
            return "Ingresa tu nombre completo";
        }
        if (!isValidEmail(email)) {
            return "Ingresa un correo electrónico válido";
        }
        if (!isValidPassword(password)) {
            return "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres";
        }
        if (!isValidAge(age)) {
            return "Ingresa una edad válida (entre " + MIN_AGE + " y " + MAX_AGE + " años)";
        }
        return null;
    }
}
