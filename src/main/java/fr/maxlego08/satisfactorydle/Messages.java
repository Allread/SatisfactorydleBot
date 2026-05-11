package fr.maxlego08.satisfactorydle;

public record Messages(MessageManager manager, String locale) {

    public String get(String key, Object... replacements) {
        return manager.get(locale, key, replacements);
    }
}
