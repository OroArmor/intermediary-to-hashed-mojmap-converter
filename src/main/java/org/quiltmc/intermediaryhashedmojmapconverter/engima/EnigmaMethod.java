package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.util.Map;

public class EnigmaMethod extends EnigmaMapping.SignatureEnigmaMapping {
    private final Map<Integer, String> parameters;

    public EnigmaMethod(String obfuscatedName, String mappedName, String comment, String signature, Map<Integer, String> parameters) {
        super(Type.METHOD, obfuscatedName, mappedName, comment, signature);
        this.parameters = parameters;
    }

    public Map<Integer, String> getParameters() {
        return parameters;
    }
}
