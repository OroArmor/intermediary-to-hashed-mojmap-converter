package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import org.jetbrains.annotations.NotNull;

public abstract class EnigmaMapping implements Comparable<EnigmaMapping> {
    private final Type type;
    private final String obfuscatedName;
    private final String mappedName;
    private final String comment;

    public EnigmaMapping(Type type, String obfuscatedName, String mappedName, String comment) {
        this.type = type;
        this.obfuscatedName = obfuscatedName;
        this.mappedName = mappedName;
        this.comment = comment;
    }

    public String getObfuscatedName() {
        return obfuscatedName;
    }

    public String getMappedName() {
        return mappedName;
    }

    public String getComment() {
        return comment;
    }

    public Type getType() {
        return type;
    }

    @Override
    public int compareTo(@NotNull EnigmaMapping o) {
        return this.obfuscatedName.compareTo(o.obfuscatedName);
    }

    public static abstract class SignatureEnigmaMapping extends EnigmaMapping {
        private final String signature;

        public SignatureEnigmaMapping(Type type, String obfuscatedName, String mappedName, String comment, String signature) {
            super(type, obfuscatedName, mappedName, comment);
            this.signature = signature;
        }

        public String getSignature() {
            return signature;
        }

        @Override
        public int compareTo(@NotNull EnigmaMapping o) {
            int res = super.compareTo(o);
            if (o instanceof SignatureEnigmaMapping && res == 0) {
                return this.signature.compareTo(((SignatureEnigmaMapping) o).signature);
            }
            return res;
        }
    }

    public enum Type {
        CLASS, FIELD, METHOD, ARG, COMMENT
    }
}
