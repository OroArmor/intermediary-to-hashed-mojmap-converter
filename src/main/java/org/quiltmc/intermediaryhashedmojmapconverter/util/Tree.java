package org.quiltmc.intermediaryhashedmojmapconverter.util;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.intermediaryhashedmojmapconverter.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class Tree<T> {
    private final Entry<T> root;
    private final List<Entry<T>> allEntries;

    public Tree(Entry<T> root, List<Entry<T>> allEntries) {
        this.root = root;
        this.allEntries = allEntries;
    }

    public static Tree<String> createParentLineTreeForLine(int line, List<String> lines) {
        Deque<Integer> entryLinesStack = new ArrayDeque<>();
        entryLinesStack.push(line);

        String currentLine = lines.get(line);
        while (currentLine.lastIndexOf("\t") >= 0) {
            int i = Util.getParentLineIndex(entryLinesStack.peek(), lines);
            entryLinesStack.push(i);
            currentLine = lines.get(i);
        }

        List<Entry<String>> currentChildren = new ArrayList<>();
        Entry<String> root = new Entry<>(lines.get(entryLinesStack.pop()), null, currentChildren);
        Entry<String> currentEntry = root;
        while (!entryLinesStack.isEmpty()) {
            List<Entry<String>> newChildren = new ArrayList<>();
            currentEntry = new Entry<>(lines.get(entryLinesStack.pop()), currentEntry, newChildren);
            currentChildren.add(currentEntry);
            currentChildren = newChildren;
        }

        List<Entry<String>> allEntries = new ArrayList<>();
        allEntries.add(root);
        walkEntryChildren(root, allEntries::add);
        return new Tree<>(root, allEntries);
    }

    public static Tree<String> createTreeFromLineList(List<String> lines) {
        List<Entry<String>> allEntries = new ArrayList<>();
        List<Entry<String>> entries = new ArrayList<>();
        Entry<String> root = new Entry<>(lines.get(0), null, entries);
        allEntries.add(root);

        AtomicInteger lineNumber = new AtomicInteger(1);
        parseLineTree(lines, lineNumber, entries, allEntries, null);
        return new Tree<>(root, allEntries);
    }

    private static void parseLineTree(List<String> lines, AtomicInteger lineNumber, Collection<Entry<String>> entries, Collection<Entry<String>> allEntries, @Nullable Entry<String> parent) {
        for (; lineNumber.get() < lines.size(); lineNumber.incrementAndGet()) {
            String line = lines.get(lineNumber.get());
            List<Entry<String>> children = new ArrayList<>();
            Entry<String> entry = new Entry<>(line.trim(), parent, children);
            entries.add(entry);
            allEntries.add(entry);
            if (lineNumber.get() < lines.size() - 1) {
                int lineIndent = line.lastIndexOf("\t");
                String nextLine = lines.get(lineNumber.get() + 1);
                int nextIndent = nextLine.lastIndexOf("\t");
                if (nextIndent > lineIndent) {
                    lineNumber.incrementAndGet();
                    parseLineTree(lines, lineNumber, children, allEntries, entry);

                    // End current tree if line after it has less indent
                    if (lineNumber.get() < lines.size() - 1) {
                        nextLine = lines.get(lineNumber.get() + 1);
                        nextIndent = nextLine.lastIndexOf("\t");
                        if (nextIndent < lineIndent) {
                            return;
                        }
                    }
                } else if (nextIndent < lineIndent) {
                    return;
                }
            }
        }
    }

    public <K> Tree<K> map(Function<T, K> valueMapper) {
        List<Entry<K>> mappedRootChildren = new ArrayList<>();
        Entry<K> mappedRoot = new Entry<>(valueMapper.apply(root.value), null, mappedRootChildren);
        mappedRootChildren.addAll(mapChildren(root, valueMapper, mappedRoot));

        List<Entry<K>> allMappedEntries = new ArrayList<>();
        Tree<K> mapped = new Tree<>(mappedRoot, allMappedEntries);
        walkEntryChildren(mappedRoot, allMappedEntries::add);
        return mapped;
    }

    private static <T, K> List<Entry<K>> mapChildren(Entry<T> entry, Function<T, K> valueMapper, Entry<K> mappedParent) {
        List<Entry<K>> mappedChildren = new ArrayList<>();

        List<Entry<K>> newChildren = new ArrayList<>();
        for (Entry<T> child : entry.children) {
            Entry<K> mapped = new Entry<>(valueMapper.apply(child.value), mappedParent, newChildren);
            mappedChildren.add(mapped);
            newChildren.addAll(mapChildren(child, valueMapper, mapped));
            newChildren = new ArrayList<>();
        }

        return mappedChildren;
    }

    public void walkEntries(Consumer<Entry<T>> visitor) {
        visitor.accept(root);
        walkEntryChildren(root, visitor);
    }

    private static <T> void walkEntryChildren(Entry<T> entry, Consumer<Entry<T>> visitor) {
        entry.children.forEach(child -> {
            visitor.accept(child);
            walkEntryChildren(child, visitor);
        });
    }

    public Collection<Entry<T>> getEndEntries() {
        List<Entry<T>> entries = new ArrayList<>();
        walkEntries(e -> {
            if (e.children.isEmpty()) {
                entries.add(e);
            }
        });
        return entries;
    }

    public Entry<T> getRoot() {
        return root;
    }

    public List<Entry<T>> getAllEntries() {
        return allEntries;
    }

    public String export() {
        StringBuilder output = new StringBuilder();
        exportEntry(root, output, -1);
        return output.toString();
    }

    public static <T> void exportEntry(Entry<T> entry, StringBuilder output, int indent) {
        output.append("\t".repeat(indent + 1));
        output.append(entry.value());
        output.append("\n");
        for (Entry<T> child : entry.children) {
            exportEntry(child, output, indent + 1);
        }
    }

    public static record Entry<T>(T value, @Nullable Entry<T> parent, List<Entry<T>> children) {
        public List<Entry<T>> getParentsFromRoot() {
            List<Entry<T>> list = new ArrayList<>();
            Entry<T> current = this;
            while (current.parent() != null) {
                current = current.parent();
                list.add(0, current);
            }
            return list;
        }

        @Override
        public String toString() {
            return "TreeEntry{" + "value=" + value + "}";
        }
    }
}
