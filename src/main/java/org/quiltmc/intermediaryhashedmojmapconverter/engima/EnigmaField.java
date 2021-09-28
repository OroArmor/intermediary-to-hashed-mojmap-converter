package org.quiltmc.intermediaryhashedmojmapconverter.engima;

public class EnigmaField extends EnigmaMapping.SignatureEnigmaMapping {
    public EnigmaField(String obfuscatedName, String mappedName, String comment, String signature) {
        super(Type.FIELD, obfuscatedName, mappedName, comment, signature);
    }
}
