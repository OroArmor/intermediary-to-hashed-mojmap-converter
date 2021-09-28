package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.util.Set;

public class EnigmaClass extends EnigmaMapping {
    private final Set<EnigmaMethod> methods;
    private final Set<EnigmaField> fields;
    private final Set<EnigmaClass> nestedClasses;

    public EnigmaClass(String obfuscatedName, String mappedName, String comment, Set<EnigmaMethod> methods, Set<EnigmaField> fields, Set<EnigmaClass> nestedClasses) {
        super(Type.CLASS, obfuscatedName, mappedName, comment);
        this.methods = methods;
        this.fields = fields;
        this.nestedClasses = nestedClasses;
    }

    public Set<EnigmaMethod> getMethods() {
        return methods;
    }

    public Set<EnigmaField> getFields() {
        return fields;
    }

    public Set<EnigmaClass> getNestedClasses() {
        return nestedClasses;
    }
}
