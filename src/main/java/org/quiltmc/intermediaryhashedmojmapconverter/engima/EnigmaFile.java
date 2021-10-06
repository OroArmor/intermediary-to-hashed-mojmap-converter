package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EnigmaFile {
    private final EnigmaClass clazz;

    public EnigmaFile(EnigmaClass clazz) {
        this.clazz = clazz;
    }

    public void export(Path path) throws IOException {
        if (!Files.exists(path)){
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, toString());
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        writeClass(clazz, builder, 0);
        return builder.toString();
    }

    private void writeClass(EnigmaClass clazz, StringBuilder builder, int indent) {
        builder.append("\t".repeat(indent)).append("CLASS ").append(clazz.getObfuscatedName());
        if (!clazz.getMappedName().isEmpty()) {
            builder.append(" ").append(clazz.getMappedName());
        }
        builder.append("\n");
        addComments(clazz, builder, indent);
        for (EnigmaField field : clazz.getFields()) {
            builder.append("\t".repeat(indent + 1)).append("FIELD ").append(field.getObfuscatedName()).append(" ");
            if(!field.getMappedName().isEmpty()) {
                builder.append(field.getMappedName()).append(" ");
            }
            builder.append(field.getSignature());
            builder.append("\n");
            addComments(field, builder, indent + 1);
        }

        for (EnigmaMethod method : clazz.getMethods()) {
            builder.append("\t".repeat(indent + 1)).append("METHOD ").append(method.getObfuscatedName()).append(" ");
            if (!method.getMappedName().isEmpty()) {
                builder.append(method.getMappedName()).append(" ");
            }
            builder.append(method.getSignature());
            builder.append("\n");
            addComments(method, builder, indent + 1);

            method.getParameters().forEach((index, parameter) -> {
                builder.append("\t".repeat(indent + 2)).append("ARG ").append(index).append(" ").append(parameter.name()).append("\n");
                if (!parameter.comment().isEmpty()) {
                    String[] commentLines = parameter.comment().split("\n");
                    for(String commentLine : commentLines) {
                        builder.append("\t".repeat(indent + 3)).append("COMMENT").append(!commentLine.isEmpty() ? " " + commentLine : "");
                        builder.append("\n");
                    }
                }
            });
        }

        for(EnigmaClass nestedClass : clazz.getNestedClasses()) {
            writeClass(nestedClass, builder, indent + 1);
        }
    }

    private void addComments(EnigmaMapping mapping, StringBuilder builder, int indent) {
        if (!mapping.getComment().isEmpty()) {
            String[] commentLines = mapping.getComment().split("\n");
            for(String commentLine : commentLines) {
                builder.append("\t".repeat(indent + 1)).append("COMMENT").append(!commentLine.isEmpty() ? " " + commentLine : "");
                builder.append("\n");
            }
        }
    }
}
