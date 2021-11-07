package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.util.ArrayList;
import java.util.List;

public class DiffBlock {
    private final int srcLine;
    private final int srcSize;
    private final int dstLine;
    private final int dstSize;
    private final List<DiffLine> diffLines;

    public DiffBlock(int srcLine, int srcSize, int dstLine, int dstSize, List<DiffLine> diffLines) {
        this.srcLine = srcLine;
        this.srcSize = srcSize;
        this.dstLine = dstLine;
        this.dstSize = dstSize;
        this.diffLines = diffLines;
    }

    public String export() {
        StringBuilder out = new StringBuilder("@@ -");
        out.append(this.srcLine);
        out.append(",");
        out.append(this.srcSize);
        out.append(" +");
        out.append(this.dstLine);
        out.append(",");
        out.append(this.dstSize);
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

    public int getSrcLine() {
        return this.srcLine;
    }

    public int getSrcSize() {
        return this.srcSize;
    }

    public int getDstLine() {
        return this.dstLine;
    }

    public int getDstSize() {
        return this.dstSize;
    }

    public int getSrcLineNumber(DiffLine line) {
        if (!line.getType().increasesSourceLineNumber()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0, j = 0; i < diffLines.size(); ++i) {
            DiffLine diffLine = diffLines.get(i);
            if (diffLine.getType().increasesSourceLineNumber()) {
                if (line.equals(diffLine)) {
                    return srcLine + j;
                }
                ++j;
            }
        }
        return -1;
    }

    public int getDstLineNumber(DiffLine line) {
        if (!line.getType().increasesDestLineNumber()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0, j = 0; i < diffLines.size(); ++i) {
            DiffLine diffLine = diffLines.get(i);
            if (diffLine.getType().increasesDestLineNumber()) {
                if (line.equals(diffLine)) {
                    return dstLine + j;
                }
                ++j;
            }
        }
        return -1;
    }
}
