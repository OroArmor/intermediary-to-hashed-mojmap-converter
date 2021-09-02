package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DiffBlock {
    private final int sourceLine;
    private final int sourceSize;
    private final int destLine;
    private final int destSize;
    private final List<Map.Entry<String, LineType>> diffLines;

    public DiffBlock(int sourceLine, int sourceSize, int destLine, int destSize, List<Map.Entry<String, LineType>> diffLines) {
        this.sourceLine = sourceLine;
        this.sourceSize = sourceSize;
        this.destLine = destLine;
        this.destSize = destSize;
        this.diffLines = diffLines;
    }

    public String export() {
        StringBuilder out = new StringBuilder("@@ -");
        out.append(this.sourceLine);
        out.append(",");
        out.append(this.sourceSize);
        out.append(" +");
        out.append(this.destLine);
        out.append(",");
        out.append(this.destSize);
        out.append(" @@\n");
        for (String line : this.getDiff()) {
            out.append(line);
            out.append("\n");
        }
        return out.toString();
    }

    public List<String> getDiff() {
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, LineType> lineEntry : this.diffLines) {
            String linePrefix;
            switch (lineEntry.getValue()) {
                case ADDED:
                    linePrefix = "+";
                    break;
                case REMOVED:
                    linePrefix = "-";
                    break;
                case UNCHANGED:
                default:
                    linePrefix = " ";
                    break;
            }
            diff.add(linePrefix + lineEntry.getKey());
        }

        return diff;
    }

    public List<String> getSource() {
        return this.filterLineType(type -> type != LineType.ADDED);
    }

    public List<String> getDestination() {
        return this.filterLineType(type -> type != LineType.REMOVED);
    }

    public List<String> getAddedLines() {
        return this.filterLineType(type -> type == LineType.ADDED);
    }

    public List<String> getRemovedLines() {
        return this.filterLineType(type -> type == LineType.REMOVED);
    }

    public List<String> getUnmodifiedLines() {
        return this.filterLineType(type -> type == LineType.UNCHANGED);
    }

    public int getSourceLine() {
        return this.sourceLine;
    }

    public int getSourceSize() {
        return this.sourceSize;
    }

    public int getDestSize() {
        return this.destSize;
    }

    public int getDestLine() {
        return this.destLine;
    }

    public List<Map.Entry<String, LineType>> getDiffLines() {
        return this.diffLines;
    }

    private List<String> filterLineType(Predicate<LineType> predicate) {
        return this.diffLines.stream().filter(entry -> predicate.test(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public enum LineType {
        REMOVED,
        ADDED,
        UNCHANGED;
    }
}
