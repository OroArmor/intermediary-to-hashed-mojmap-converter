package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.util.List;

public class Diff {
    private final String from;
    private final String to;
    private final List<DiffBlock> blocks;
    private final List<String> info;

    public Diff(String from, String to, List<DiffBlock> blocks, List<String> info) {
        this.from = from;
        this.to = to;
        this.blocks = blocks;
        this.info = info;
    }

    public String export() {
        StringBuilder out = new StringBuilder();
        for (String info : this.getInfo()) {
            out.append(info);
            out.append("\n");
        }
        out.append("--- a/");
        out.append(this.getFrom());
        out.append("\n+++ b/");
        out.append(this.getTo());
        out.append("\n");
        for (DiffBlock block : this.getBlocks()) {
            out.append(block.export());
        }
        return out.toString();
    }

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public List<DiffBlock> getBlocks() {
        return this.blocks;
    }

    public List<String> getInfo() {
        return this.info;
    }
}
