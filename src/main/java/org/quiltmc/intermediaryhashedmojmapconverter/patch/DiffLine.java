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

    public String toString() {
        return this.getDiffFormattedLine();
    }

    public enum LineType {
        REMOVED(true, false),
        ADDED(false, true),
        UNCHANGED(true, true);

        private final boolean increasesSourceLineNumber;
        private final boolean increasesDestLineNumber;

        LineType(boolean increasesSourceLineNumber, boolean increasesDestLineNumber) {
            this.increasesSourceLineNumber = increasesSourceLineNumber;
            this.increasesDestLineNumber = increasesDestLineNumber;
        }

        public boolean increasesSourceLineNumber() {
            return this.increasesSourceLineNumber;
        }

        public boolean increasesDestLineNumber() {
            return this.increasesDestLineNumber;
        }
    }
}
