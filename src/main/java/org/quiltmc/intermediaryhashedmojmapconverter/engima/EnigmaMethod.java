package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.util.Map;

public class EnigmaMethod extends EnigmaMapping.SignatureEnigmaMapping {
    private final Map<Integer, Parameter> parameters;

    public EnigmaMethod(String obfuscatedName, String mappedName, String comment, String signature, Map<Integer, Parameter> parameters) {
        super(Type.METHOD, obfuscatedName, mappedName, comment, signature);
        this.parameters = parameters;
    }

    public Map<Integer, Parameter> getParameters() {
        return parameters;
    }

    public record Parameter(String name, String comment) {
    }
}
