package org.quiltmc.intermediaryhashedmojmapconverter.engima;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

public class EnigmaReader {
    private static final ObfuscatedNameVisitor DEFAULT_VISITOR = (type, original, signature, isMethod) -> original;

    public static EnigmaFile readFile(Path path) throws IOException {
        return readFile(path, null);
    }

    public static EnigmaFile readFile(Path path, @Nullable ObfuscatedNameVisitor visitor) throws IOException {
        if (visitor == null) {
            visitor = DEFAULT_VISITOR;
        }

        List<String> lines = Files.readAllLines(path);

        AtomicInteger currentLine = new AtomicInteger(0);

        return new EnigmaFile(parseClass(lines, currentLine, visitor));
    }

    public static EnigmaFile readLines(List<String> lines) {
        return readLines(lines, null);
    }

    public static EnigmaFile readLines(List<String> lines, @Nullable ObfuscatedNameVisitor visitor) {
        if (visitor == null) {
            visitor = DEFAULT_VISITOR;
        }

        AtomicInteger currentLine = new AtomicInteger(0);

        return new EnigmaFile(parseClass(lines, currentLine, visitor));
    }

    private static EnigmaClass parseClass(List<String> lines, AtomicInteger currentLine, ObfuscatedNameVisitor visitor) {
        Set<EnigmaMethod> methods = new TreeSet<>();
        Set<EnigmaField> fields = new TreeSet<>();
        Set<EnigmaClass> nestedClasses = new TreeSet<>();
        String name;
        String obfuscatedName;
        StringBuilder comment = new StringBuilder();

        String line = lines.get(currentLine.getAndIncrement());
        int currentIndent = line.indexOf(EnigmaMapping.Type.CLASS.name());
        String[] tokens = line.trim().split("\\s+");

        obfuscatedName = visitor.visit(EnigmaMapping.Type.CLASS, tokens[1], false, false);
        name = tokens.length < 3 ? "" : tokens[2];

        for (; currentLine.get() < lines.size(); currentLine.getAndIncrement()) {
            line = lines.get(currentLine.get());
            if (line.lastIndexOf("\t") < currentIndent) {
                currentLine.decrementAndGet();
                return new EnigmaClass(obfuscatedName, name, comment.toString(), methods, fields, nestedClasses);
            }
            tokens = line.trim().split("\\s+");
            switch (EnigmaMapping.Type.valueOf(tokens[0])) {
                case COMMENT -> addComment(comment, line);
                case FIELD -> fields.add(parseField(lines, currentLine, visitor));
                case METHOD -> methods.add(parseMethod(lines, currentLine, visitor));
                case CLASS -> nestedClasses.add(parseClass(lines, currentLine, visitor));
                default -> throw new IllegalArgumentException("Unexpected line:\n" + line);
            }
        }

        return new EnigmaClass(obfuscatedName, name, comment.toString(), methods, fields, nestedClasses);
    }

    private static void addComment(StringBuilder comment, String line) {
        if (line.trim().length() > 8) {
            comment.append(line.trim().substring(8));
        }
        comment.append("\n");
    }

    private static EnigmaMethod parseMethod(List<String> lines, AtomicInteger currentLine, ObfuscatedNameVisitor visitor) {
        String name;
        String obfuscatedName;
        StringBuilder comment = new StringBuilder();
        String signature;
        Map<Integer, String> parameters = new TreeMap<>();

        String line = lines.get(currentLine.getAndIncrement());
        String[] tokens = line.trim().split("\\s+");

        String visited = visitor.visit(EnigmaMapping.Type.METHOD, tokens[1] + ";" + (tokens.length < 4 ? tokens[2] : tokens[3]), true, true);

        obfuscatedName = visited.substring(0, visited.indexOf(";"));
        name = tokens.length < 4 ? "" : tokens[2];
        signature = visited.substring(visited.indexOf(";") + 1);

        for (; currentLine.get() < lines.size(); currentLine.getAndIncrement()) {
            line = lines.get(currentLine.get());
            tokens = line.trim().split("\\s+");
            if (EnigmaMapping.Type.valueOf(tokens[0]) == EnigmaMapping.Type.COMMENT) {
                addComment(comment, line);
            } else if (EnigmaMapping.Type.valueOf(tokens[0]) == EnigmaMapping.Type.ARG){
                parameters.put(Integer.parseInt(tokens[1]), tokens[2]);
            } else {
                currentLine.decrementAndGet();
                break;
            }
        }

        return new EnigmaMethod(obfuscatedName, name, comment.toString(), signature, parameters);
    }

    private static EnigmaField parseField(List<String> lines, AtomicInteger currentLine, ObfuscatedNameVisitor visitor) {
        String name;
        String obfuscatedName;
        StringBuilder comment = new StringBuilder();
        String signature;

        String line = lines.get(currentLine.getAndIncrement());
        String[] tokens = line.trim().split("\\s+");

        String visited = visitor.visit(EnigmaMapping.Type.FIELD, tokens[1] + ";" + (tokens.length < 4 ? tokens[2] : tokens[3]), true, false);

        obfuscatedName = visited.substring(0, visited.indexOf(";"));
        name = tokens.length < 4 ? "" : tokens[2];
        signature = visited.substring(visited.indexOf(";") + 1);

        for (; currentLine.get() < lines.size(); currentLine.getAndIncrement()) {
            line = lines.get(currentLine.get());
            tokens = line.trim().split("\\s+");
            if (EnigmaMapping.Type.valueOf(tokens[0]) == EnigmaMapping.Type.COMMENT) {
                addComment(comment, line);
            } else {
                currentLine.decrementAndGet();
                break;
            }
        }

        return new EnigmaField(obfuscatedName, name, comment.toString(), signature);
    }

    public interface ObfuscatedNameVisitor {
        String visit(EnigmaMapping.Type type, String original, boolean signature, boolean isMethod);
    }
}
