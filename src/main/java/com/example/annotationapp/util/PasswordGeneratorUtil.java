package com.example.annotationapp.util;

import org.apache.commons.lang3.RandomStringUtils; // Nécessite Apache Commons Lang
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PasswordGeneratorUtil {

    private static final int DEFAULT_LENGTH = 12; // Longueur du mot de passe

    // Caractères possibles pour chaque catégorie
    private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+<>?";

    private static final String ALL_ALLOWED_CHARS = UPPERCASE_CHARS + LOWERCASE_CHARS + DIGITS + SPECIAL_CHARS;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Génère un mot de passe aléatoire sécurisé.
     * Inclut au moins une majuscule, une minuscule, un chiffre et un caractère spécial.
     * @param length Longueur souhaitée du mot de passe (minimum 8)
     * @return Le mot de passe généré.
     */
    public static String generateSecurePassword(int length) {
        if (length < 8) {
            length = 8; // Longueur minimale pour inclure tous les types de caractères
        }

        StringBuilder password = new StringBuilder(length);

        // 1. S'assurer d'avoir au moins un caractère de chaque type requis
        password.append(getRandomChar(UPPERCASE_CHARS));
        password.append(getRandomChar(LOWERCASE_CHARS));
        password.append(getRandomChar(DIGITS));
        password.append(getRandomChar(SPECIAL_CHARS));

        // 2. Remplir le reste du mot de passe avec des caractères aléatoires parmi tous les types
        for (int i = 4; i < length; i++) {
            password.append(getRandomChar(ALL_ALLOWED_CHARS));
        }

        // 3. Mélanger les caractères pour éviter une structure prévisible
        List<Character> charList = password.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        Collections.shuffle(charList, random);

        return charList.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    public static String generateDefaultPassword() {
        return generateSecurePassword(DEFAULT_LENGTH);
    }

    private static char getRandomChar(String characterSet) {
        int randomIndex = random.nextInt(characterSet.length());
        return characterSet.charAt(randomIndex);
    }

    // Alternative plus simple utilisant Apache Commons Lang (moins de contrôle sur la composition)
    public static String generateSimplePasswordApacheCommons(int length) {
        // Génère une chaîne aléatoire alphanumérique de la longueur spécifiée
        return RandomStringUtils.randomAlphanumeric(length);
    }
}