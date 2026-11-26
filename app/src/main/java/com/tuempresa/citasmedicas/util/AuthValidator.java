package com.tuempresa.citasmedicas.util;

import java.util.regex.Pattern;

/**
 * Reglas de validación de autenticación.
 * <p>
 * Clase SIN dependencias de Android a propósito: así puede probarse en una JVM
 * normal (JUnit) sin Robolectric ni emulador.
 */
public final class AuthValidator {

    /**
     * Mínimo de contraseña para la validación local.
     * <p>
     * Firebase Authentication exige <b>6</b>; con Firebase conectado, una contraseña
     * más corta la rechaza el servidor con su propio mensaje. Este mínimo local se
     * mantiene en 4 para no invalidar cuentas creadas antes.
     */
    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MIN_AGE = 1;
    public static final int MAX_AGE = 120;
    public static final int MIN_NAME_LENGTH = 3;
    /** Mínimo de dígitos de un teléfono (sin contar espacios, guiones ni el +). */
    public static final int MIN_PHONE_DIGITS = 7;

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
     * ¿El teléfono tiene pinta de número real?
     * <p>
     * Se cuentan sólo los dígitos, de modo que {@code +34 611 22 03 31},
     * {@code 611-22-03-31} y {@code 611220331} son todos válidos.
     */
    public static boolean isValidPhone(String phone) {
        if (isBlank(phone)) {
            return false;
        }
        int digitos = 0;
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (Character.isDigit(c)) {
                digitos++;
            } else if (c != ' ' && c != '+' && c != '-' && c != '(' && c != ')') {
                // Cualquier otro carácter (letras, símbolos) invalida el teléfono.
                return false;
            }
        }
        return digitos >= MIN_PHONE_DIGITS;
    }

    /**
     * Valida el expediente médico que se pide al paciente.
     *
     * @return {@code null} si es válido, o el mensaje de error en español.
     */
    public static String validateMedicalRecord(String phone) {
        if (isBlank(phone)) {
            return "Ingresa tu número de teléfono";
        }
        if (!isValidPhone(phone)) {
            return "El teléfono no es válido: escribe al menos "
                    + MIN_PHONE_DIGITS + " dígitos";
        }
        return null;
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
