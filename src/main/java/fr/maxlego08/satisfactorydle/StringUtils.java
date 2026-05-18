package fr.maxlego08.satisfactorydle;

import java.text.Normalizer;

public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Normalizes a string by removing diacritical marks (accents, cedillas, etc.)
     * and converting to lowercase for flexible comparison.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }
}
