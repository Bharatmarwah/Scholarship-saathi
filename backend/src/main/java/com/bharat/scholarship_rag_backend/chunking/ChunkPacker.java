package com.bharat.scholarship_rag_backend.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bharat.scholarship_rag_backend.chunking.MarkdownStructureParser.Block;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * Fills blocks into chunks against a real token budget.
 *
 * <p>Blocks are never cut in half, so a section, a list, a table row or a FAQ
 * answer survives intact wherever that is possible. A single block larger than
 * the ceiling is the only thing that must break, and it breaks on line breaks
 * inside itself rather than mid-sentence.
 *
 * <p>Very small blocks are folded into a neighbour when they fit. That is what
 * reattaches the orphaned PIN-code fragments the cleaner split off mid annexure
 * row ({@code …Under} / {@code 14} / {@code Pradesh Graduate}) without the
 * chunker having to guess where the break belongs.
 */
final class
ChunkPacker {

    /** Comfortable size. Chunks land near this unless structure forces otherwise. */
    private static final int TARGET_TOKENS = 500;

    /** Hard ceiling. Exceeding it costs embedding quality and context budget. */
    private static final int MAX_TOKENS = 800;

    /** Blocks at or below this are small enough to merge into a neighbour. */
    private static final int SMALL_BLOCK_TOKENS = 24;

    private final TokenCounter counter;

    /**
     * One {@link TokenTextSplitter} per budget, reused across calls. Budgets
     * vary (a whole section, a rendered part) but repeat, so caching avoids
     * rebuilding the tokenizer wiring for every oversized block.
     */
    private final Map<Integer, TokenTextSplitter> splitters = new HashMap<>();

    ChunkPacker(TokenCounter counter) {
        this.counter = counter;
    }

    /** One packed group of blocks plus the page span it covers. */
    record Group(List<MarkdownStructureParser.Block> blocks, Integer pageStart, Integer pageEnd) {
    }

/**
     * @param budget  tokens available for the body once the caller's context
     *                header and continuation marker have been charged. Passed in
     *                rather than derived here so the header is always counted
     *                against the ceiling.
     * @param leading blocks that must open the first chunk, used for the context
     *                header of a section so a heading is never separated from its
     *                text
     * @param overlap whether to repeat trailing blocks into the next chunk
     */
    List<Group> pack(List<Block> blocks,
                     int budget,
                     List<Block> leading,
                     boolean overlap) {

        List<Group> groups = new ArrayList<>();
        if (blocks.isEmpty()) {
            return groups;
        }
        budget = Math.max(1, budget);

        List<MarkdownStructureParser.Block> current = new ArrayList<>(leading);
        int currentTokens = 0;
        for (MarkdownStructureParser.Block block : leading) {
            currentTokens += counter.count(block.text());
        }

        for (MarkdownStructureParser.Block block : blocks) {
            int blockTokens = counter.count(block.text());

            boolean fits = (current.isEmpty() && leading.isEmpty()) || currentTokens + blockTokens <= budget;
            if (fits) {
                current.add(block);
                currentTokens += blockTokens;
                continue;
            }

            groups.add(new Group(new ArrayList<>(current), pageStart(current), pageEnd(current)));

            List<MarkdownStructureParser.Block> next = new ArrayList<>();
            int nextTokens = 0;

            // Carry a trailing block forward when it is small and overlaps are
            // wanted, so a sentence or list item straddling the cut is still
            // complete in the following chunk.
            if (overlap) {
                MarkdownStructureParser.Block tail = current.get(current.size() - 1);
                int tailTokens = counter.count(tail.text());
                if (tailTokens <= SMALL_BLOCK_TOKENS && tailTokens + blockTokens <= budget) {
                    next.add(tail);
                    nextTokens = tailTokens;
                }
            }

            next.add(block);
            nextTokens += blockTokens;
            current = next;
            currentTokens = nextTokens;
        }

        if (!current.isEmpty()) {
            groups.add(new Group(new ArrayList<>(current), pageStart(current), pageEnd(current)));
        }
        return groups;
    }

    /**
     * Splits a block that alone exceeds the ceiling. Only used for the
     * flattened annexure tables, where the source itself put many records into
     * one unbroken paragraph.
     *
     * <p>The break is delegated to Spring AI's {@link TokenTextSplitter}, which
     * packs whole tokens up to the budget and prefers to end a piece at the last
     * sentence punctuation it contains, so no word is damaged and a sentence
     * boundary is preferred over an arbitrary mid-sentence cut. It runs on the
     * same {@code cl100k_base} vocabulary as {@link TokenCounter}, so the pieces
     * it returns are guaranteed to fit the budget the caller measured against.
     */
    List<MarkdownStructureParser.Block> splitOversized(MarkdownStructureParser.Block block,
                                                      int budget) {
        if (budget <= 0 || counter.count(block.text()) <= budget) {
            return List.of(block);
        }

        List<String> pieces = splitter(budget).split(Document.builder().text(block.text()).build())
                .stream()
                .map(Document::getText)
                .toList();

        // The splitter drops whitespace-only fragments; never lose the block.
        if (pieces.isEmpty()) {
            return List.of(block);
        }

        List<MarkdownStructureParser.Block> parts = new ArrayList<>(pieces.size());
        for (String piece : pieces) {
            parts.add(new MarkdownStructureParser.Block(
                    MarkdownStructureParser.Kind.PARAGRAPH, piece, block.page(), false, null));
        }
        return parts;
    }

    /**
     * One splitter per budget, reused across calls. Budgets vary (a whole
     * section, a rendered part) but repeat, so caching avoids rebuilding the
     * tokenizer wiring for every oversized block.
     */
    private TokenTextSplitter splitter(int chunkSize) {
        int safeSize = Math.max(1, chunkSize);
        return splitters.computeIfAbsent(safeSize, size -> TokenTextSplitter.builder()
                .withChunkSize(size)
                .withMinChunkLengthToEmbed(1)
                .withKeepSeparator(true)
                .build());
    }

    int targetTokens() {
        return TARGET_TOKENS;
    }

    int maxTokens() {
        return MAX_TOKENS;
    }

    private static Integer pageStart(List<MarkdownStructureParser.Block> blocks) {
        for (MarkdownStructureParser.Block block : blocks) {
            if (block.page() != null) {
                return block.page();
            }
        }
        return null;
    }

    private static Integer pageEnd(List<MarkdownStructureParser.Block> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i).page() != null) {
                return blocks.get(i).page();
            }
        }
        return null;
    }
}