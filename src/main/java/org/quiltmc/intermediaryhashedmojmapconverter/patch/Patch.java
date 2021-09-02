package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Patch {
    private static final Pattern BLOCK_HEADER_REGEX = Pattern.compile("@@ -([\\d]+),([\\d]+) \\+([\\d]+),([\\d]+) @@");
    private final List<String> header;
    private final List<Diff> diffs;
    private final List<String> footer;

    public static Patch read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        // Patch data
        List<String> header = new ArrayList<>();
        List<Diff> diffs = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        boolean readingFooter = false;

        // Diff data
        boolean readingDiff = false;
        String diffFrom = "";
        String diffTo = "";
        List<DiffBlock> diffBlocks = new ArrayList<>();
        List<String> diffInfo = new ArrayList<>();

        // Block data
        boolean readingBlock = false;
        List<Map.Entry<String, DiffBlock.LineType>> blockDiffLines = new ArrayList<>();
        int blockFromLine = -1;
        int blockFromSize = -1;
        int blockFromRemaining = -1;
        int blockToLine = -1;
        int blockToSize = -1;
        int blockToRemaining = -1;

        for (int l = 0; l < lines.size(); ++l) {
            String line = lines.get(l);
            // Read patch
            if (line.startsWith("diff") && line.split("\\s+").length >= 3) {
                if (!readingDiff) {
                    readingDiff = true;
                } else {
                    // Save current diff
                    diffs.add(new Diff(diffFrom, diffTo, List.copyOf(diffBlocks), List.copyOf(diffInfo)));
                    // Clear diff data
                    diffFrom = "";
                    diffTo = "";
                    diffBlocks.clear();
                    diffInfo.clear();
                    // Clear block data
                    readingBlock = false;
                    blockDiffLines.clear();
                    blockFromLine = -1;
                    blockFromSize = -1;
                    blockFromRemaining = -1;
                    blockToLine = -1;
                    blockToSize = -1;
                    blockToRemaining = -1;
                }

                diffInfo.add(line); // save command
                continue;
            }
            if (!readingFooter && !readingBlock && line.equals("-- ")) { // a line inside a block won't return true for equals("-- ")
                readingFooter = true;
                footer.add(line);
                continue;
            } else if (!readingDiff) {
                header.add(line);
                continue;
            }

            if (readingFooter) {
                footer.add(line);
                continue;
            }

            // Read diff
            if (!readingBlock) {
                if (line.startsWith("--- ") && line.length() > 4 && l < lines.size() - 1) {
                    String nextLine = lines.get(l + 1);
                    if (nextLine.startsWith("+++ ") && nextLine.length() > 4) {
                        diffFrom = skipPathPrefix(line.substring(4));
                        diffTo = skipPathPrefix(nextLine.substring(4));
                        ++l; // skip a line
                    }
                } else if (line.startsWith("@@")) {
                    // Read block header
                    Matcher matcher = BLOCK_HEADER_REGEX.matcher(line);
                    if (matcher.find()) {
                        readingBlock = true;
                        blockFromLine = Integer.parseInt(matcher.group(1));
                        blockFromSize = Integer.parseInt(matcher.group(2));
                        blockToLine = Integer.parseInt(matcher.group(3));
                        blockToSize = Integer.parseInt(matcher.group(4));

                        blockFromRemaining = blockFromSize;
                        blockToRemaining = blockToSize;
                    }
                } else {
                    diffInfo.add(line);
                }
                continue;
            }

            // Read block
            String fileLine = line.substring(1); // remove diff indent
            if (line.startsWith("-")) {
                blockDiffLines.add(new AbstractMap.SimpleImmutableEntry<>(fileLine, DiffBlock.LineType.REMOVED));
                --blockFromRemaining;
            } else if (line.startsWith("+")) {
                blockDiffLines.add(new AbstractMap.SimpleImmutableEntry<>(fileLine, DiffBlock.LineType.ADDED));
                --blockToRemaining;
            } else {
                blockDiffLines.add(new AbstractMap.SimpleImmutableEntry<>(fileLine, DiffBlock.LineType.UNCHANGED));
                --blockFromRemaining;
                --blockToRemaining;
            }

            // Block end
            if (blockFromRemaining <= 0 && blockToRemaining <= 0) {
                readingBlock = false;
                diffBlocks.add(new DiffBlock(blockFromLine, blockFromSize, blockToLine, blockToSize, List.copyOf(blockDiffLines)));
                blockDiffLines.clear();
            }
        }

        // Save the last diff
        if (!diffInfo.isEmpty()) {
            diffs.add(new Diff(diffFrom, diffTo, List.copyOf(diffBlocks), List.copyOf(diffInfo)));
        }

        return new Patch(header, diffs, footer);
    }

    private static String skipPathPrefix(String input) {
        Path path = Path.of(input);
        StringBuilder output = new StringBuilder();
        for (int i = 1; i < path.getNameCount(); ++i) {
            output.append(path.getName(i));
            if (i < path.getNameCount() - 1) {
                output.append("/");
            }
        }
        return output.toString();
    }

    public Patch(List<String> header, List<Diff> diffs, List<String> footer) {
        this.header = header;
        this.diffs = diffs;
        this.footer = footer;
    }

    public void apply(BufferedReader reader, BufferedWriter writer, String filename) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        List<String> newLines = this.apply(lines, filename);
        for (String l : newLines) {
            writer.write(l);
            writer.newLine();
        }
    }

    public List<String> apply(List<String> lines, String filename) {
        Diff diff = null;
        for (Diff d : this.diffs) {
            if (d.getFrom().contains(filename)) {
                diff = d;
            }
        }
        if (diff == null) {
            throw new IllegalArgumentException("This patch can not be applied to " + filename);
        }

        List<String> out = new ArrayList<>();
        int i = 0;
        for (DiffBlock block : diff.getBlocks()) {
            out.addAll(block.getDestination());
            i += block.getSourceSize();
        }

        // Add remaining lines to the result
        for (; i < lines.size(); ++i) {
            out.add(lines.get(i));
        }
        return out;
    }

    public String export() {
        StringBuilder out = new StringBuilder();
        for (String line : this.getHeader()) {
            out.append(line);
            out.append("\n");
        }
        for (Diff diff : this.getDiffs()) {
            out.append(diff.export());
        }
        for (String line : this.getFooter()) {
            out.append(line);
            out.append("\n");
        }
        return out.toString();
    }

    public List<String> getHeader() {
        return this.header;
    }

    public List<Diff> getDiffs() {
        return this.diffs;
    }

    public List<String> getFooter() {
        return this.footer;
    }
}