package org.quiltmc.intermediaryhashedmojmapconverter.patch;

public class DiffLine {
    private final String line;
    private final LineType type;

    public DiffLine(String line, LineType type) {
        this.line = line;
        this.type = type;
    }

    public String getLine() {
        return this.line;
    }

    public LineType getType() {
        return this.type;
    }

    public String getDiffFormattedLine() {
        return (this.getType() == LineType.REMOVED ? "-" : (this.type == LineType.ADDED ? "+" : " ")) + this.getLine();
    }

    public enum LineType {
        REMOVED,
        ADDED,
        UNCHANGED;
    }
}
