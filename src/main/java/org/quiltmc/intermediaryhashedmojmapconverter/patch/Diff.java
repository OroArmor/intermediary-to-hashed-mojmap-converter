package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.util.List;

public class Diff {
    private final String src;
    private final String dst;
    private final List<DiffBlock> blocks;
    private final List<String> info;

    public Diff(String src, String dst, List<DiffBlock> blocks, List<String> info) {
        this.src = src;
        this.dst = dst;
        this.blocks = blocks;
        this.info = info;
    }

    public String export() {
        StringBuilder out = new StringBuilder();
        for (String info : this.getInfo()) {
            out.append(info);
            out.append("\n");
        }
        out.append("--- ");
        out.append(this.getSrc().startsWith("/") ? this.getSrc() : "a/" + this.getSrc());
        out.append("\n+++ ");
        out.append(this.getDst().startsWith("/") ? this.getDst() : "b/" + this.getDst());
        out.append("\n");
        for (DiffBlock block : this.getBlocks()) {
            out.append(block.export());
        }
        return out.toString();
    }

    public String getSrc() {
        return this.src;
    }

    public String getDst() {
        return this.dst;
    }

    public List<DiffBlock> getBlocks() {
        return this.blocks;
    }

    public List<String> getInfo() {
        return this.info;
    }
}
