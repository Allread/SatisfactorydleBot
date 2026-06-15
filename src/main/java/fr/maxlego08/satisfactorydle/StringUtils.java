package fr.maxlego08.satisfactorydle;

import java.text.Normalizer;

public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Normalizes a string for flexible quiz answer comparison:
     * - removes diacritical marks (accents, cedillas, etc.)
     * - converts to lowercase
     * - replaces hyphens, apostrophes, underscores and similar separators with spaces
     * - collapses multiple spaces into one and trims the result
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noAccents = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
        String noSeparators = noAccents.replaceAll("[\\-_''`]", " ");
        return noSeparators.replaceAll("\\s+", " ").trim();
    }
}
