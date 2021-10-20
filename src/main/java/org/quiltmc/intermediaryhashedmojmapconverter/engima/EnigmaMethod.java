package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public class EnigmaMethod extends EnigmaMapping.SignatureEnigmaMapping {
    private final List<EngimaParameter> parameters;

    public EnigmaMethod(String obfuscatedName, String mappedName, String comment, String signature, List<EngimaParameter> parameters) {
        super(Type.METHOD, obfuscatedName, mappedName, comment, signature);
        this.parameters = parameters;
    }

    public List<EngimaParameter> getParameters() {
        return parameters;
    }

    public record EngimaParameter(int index, String name, String comment) implements Comparable<EngimaParameter>{
        @Override
        public int compareTo(@NotNull EngimaParameter o) {
            return Integer.compare(this.index, o.index);
        }
    }
}
