package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.util.ArrayList;
import java.util.List;

public class DiffBlock {
    private final int sourceLine;
    private final int sourceSize;
    private final int destLine;
    private final int destSize;
    private final List<DiffLine> diffLines;

    public DiffBlock(int sourceLine, int sourceSize, int destLine, int destSize, List<DiffLine> diffLines) {
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
        for (DiffLine lineEntry : this.diffLines) {
            diff.add(lineEntry.getDiffFormattedLine());
        }

        return diff;
    }

    public List<String> getDestination() {
        List<String> res = new ArrayList<>();
        for (DiffLine diffLine : this.getDiffLines()) {
            if (diffLine.getType() != DiffLine.LineType.REMOVED) {
                res.add(diffLine.getLine());
            }
        }

        return res;
    }

    public List<DiffLine> getDiffLines() {
        return this.diffLines;
    }

    public int getSourceLine() {
        return this.sourceLine;
    }

    public int getSourceSize() {
        return this.sourceSize;
    }

    public int getDestLine() {
        return this.destLine;
    }

    public int getDestSize() {
        return this.destSize;
    }

    public int getSourceLineNumber(DiffLine line) {
        if (!line.getType().increasesSourceLineNumber()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0, j = 0; i < diffLines.size(); ++i) {
            DiffLine diffLine = diffLines.get(i);
            if (diffLine.getType().increasesSourceLineNumber()) {
                if (line.equals(diffLine)) {
                    return sourceLine + j;
                }
                ++j;
            }
        }
        return -1;
    }

    public int getDestLineNumber(DiffLine line) {
        if (!line.getType().increasesDestLineNumber()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0, j = 0; i < diffLines.size(); ++i) {
            DiffLine diffLine = diffLines.get(i);
            if (diffLine.getType().increasesDestLineNumber()) {
                if (line.equals(diffLine)) {
                    return destLine + j;
                }
                ++j;
            }
        }
        return -1;
    }
}
