package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Patch {
    private static final Pattern BLOCK_HEADER_REGEX = Pattern.compile("@@ -([\\d]+)(?:,([\\d]+))? \\+([\\d]+)(?:,([\\d]+))? @@");
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
        String diffSrc = "";
        String diffDst = "";
        List<DiffBlock> diffBlocks = new ArrayList<>();
        List<String> diffInfo = new ArrayList<>();

        // Block data
        boolean readingBlock = false;
        List<DiffLine> blockDiffLines = new ArrayList<>();
        int blockSrcLine = -1;
        int blockSrcSize = -1;
        int blockSrcRemaining = -1;
        int blockDstLine = -1;
        int blockDstSize = -1;
        int blockDstRemaining = -1;

        for (int l = 0; l < lines.size(); ++l) {
            String line = lines.get(l);
            // Read patch
            if (line.startsWith("diff") && line.split("\\s+").length >= 3) {
                if (!readingDiff) {
                    readingDiff = true;
                } else {
                    // Save current diff
                    diffs.add(new Diff(diffSrc, diffDst, List.copyOf(diffBlocks), List.copyOf(diffInfo)));
                    // Clear diff data
                    diffSrc = "";
                    diffDst = "";
                    diffBlocks.clear();
                    diffInfo.clear();
                    // Clear block data
                    readingBlock = false;
                    blockDiffLines.clear();
                    blockSrcLine = -1;
                    blockSrcSize = -1;
                    blockSrcRemaining = -1;
                    blockDstLine = -1;
                    blockDstSize = -1;
                    blockDstRemaining = -1;
                }

                diffInfo.add(line); // save command
                continue;
            }
            if (!readingFooter && !readingBlock && (line.equals("--") || line.equals("-- "))) { // a line inside a block won't return true for equals("-- ")
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
                        diffSrc = skipPathPrefix(line.substring(4));
                        diffDst = skipPathPrefix(nextLine.substring(4));
                        ++l; // skip a line
                    }
                } else if (line.startsWith("@@")) {
                    // Read block header
                    Matcher matcher = BLOCK_HEADER_REGEX.matcher(line);
                    if (matcher.find()) {
                        readingBlock = true;
                        blockSrcLine = Integer.parseInt(matcher.group(1));
                        if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                            blockSrcSize = Integer.parseInt(matcher.group(2));
                        } else {
                            blockSrcSize = 1;
                        }
                        blockDstLine = Integer.parseInt(matcher.group(3));
                        if (matcher.group(4) != null && !matcher.group(4).isEmpty()) {
                            blockDstSize = Integer.parseInt(matcher.group(4));
                        } else {
                            blockDstSize = 1;
                        }

                        blockSrcRemaining = blockSrcSize;
                        blockDstRemaining = blockDstSize;
                    } else {
                        throw new IllegalStateException("Invalid block header " + line);
                    }
                } else {
                    diffInfo.add(line);
                }
                continue;
            }

            // Read block
            String fileLine = line.substring(1); // remove diff indent
            if (line.startsWith("-")) {
                blockDiffLines.add(new DiffLine(fileLine, DiffLine.LineType.REMOVED));
                --blockSrcRemaining;
            } else if (line.startsWith("+")) {
                blockDiffLines.add(new DiffLine(fileLine, DiffLine.LineType.ADDED));
                --blockDstRemaining;
            } else {
                blockDiffLines.add(new DiffLine(fileLine, DiffLine.LineType.UNCHANGED));
                --blockSrcRemaining;
                --blockDstRemaining;
            }

            // Block end
            if (blockSrcRemaining <= 0 && blockDstRemaining <= 0) {
                readingBlock = false;
                diffBlocks.add(new DiffBlock(blockSrcLine, blockSrcSize, blockDstLine, blockDstSize, List.copyOf(blockDiffLines)));
                blockDiffLines.clear();
            }
        }

        // Save the last diff
        if (!diffInfo.isEmpty()) {
            diffs.add(new Diff(diffSrc, diffDst, List.copyOf(diffBlocks), List.copyOf(diffInfo)));
        }

        return new Patch(header, diffs, footer);
    }

    private static String skipPathPrefix(String input) {
        if (input.startsWith("/")) {
            return input;
        }

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

    public static void applyDiff(BufferedReader reader, BufferedWriter writer, Diff diff) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        List<String> newLines = applyDiff(lines, diff);
        for (String l : newLines) {
            writer.write(l);
            writer.newLine();
        }
    }

    public static List<String> applyDiff(List<String> lines, Diff diff) {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (DiffBlock block : diff.getBlocks()) {
            for (int j = i; j < block.getSrcLine() - 1; ++j, ++i) {
                out.add(lines.get(j));
            }
            for (DiffLine diffLine : block.getDiffLines()) {
                switch (diffLine.getType()) {
                    case ADDED -> out.add(diffLine.getLine());
                    case REMOVED -> ++i;
                    case UNCHANGED -> {
                        out.add(diffLine.getLine());
                        ++i;
                    }
                }
            }
        }

        // Add remaining lines to the result
        for (; i < lines.size(); ++i) {
            out.add(lines.get(i));
        }
        return out;
    }

    public List<String> getModifiedFiles() {
        List<String> dstFiles = new ArrayList<>();
        for (Diff diff : diffs) {
            if (!diff.getDst().equals("/dev/null")) {
                dstFiles.add(diff.getDst());
            }
        }
        return dstFiles;
    }

    public List<String> getRemovedFiles() {
        List<String> removedFiles = new ArrayList<>();
        for (Diff diff : diffs) {
            if (diff.getDst().equals("/dev/null")) {
                removedFiles.add(diff.getSrc());
            }
        }
        return removedFiles;
    }

    public List<String> getAddedFiles() {
        List<String> addedFiles = new ArrayList<>();
        for (Diff diff : diffs) {
            if (diff.getSrc().equals("/dev/null")) {
                addedFiles.add(diff.getDst());
            }
        }
        return addedFiles;
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
