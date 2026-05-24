package dev.mrsusu.skin;

/**
 * Immutable record holding a Mojang skin's texture value and signature.
 * Used with {@link com.mojang.authlib.properties.Property} to apply skins.
 */
public record SkinData(String value, String signature) {

    /** A safe default (Steve skin) used as fallback when fetch fails. */
    public static final SkinData FALLBACK = new SkinData(
            // Steve's default base64 texture value (vanilla default)
            "ewogICJ0aW1lc3RhbXAiIDogMTU5MDg1OTMwMjYzMSwKICAicHJvZmlsZUlkIiA6ICI4NjY3YmE3" +
            "MWI4NTU0NWE0OWJlNDgxM2MxYjllNzQ2MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdGV2ZSIsCiAg" +
            "InRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1" +
            "cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82MGE1YmQwMTZiM2M5YTFiOTk5NmVmYWQ2MzllMGZm" +
            "NGRlMzgxMjNhYTY0ZGE0ZTc5NzE0ZWViNmE1MWU2NiIKICAgIH0KICB9Cn0=",
            "" // Steve has no signature requirement for display
    );

    public boolean isValid() {
        return value != null && !value.isBlank();
    }
}
