package com.bharat.scholarship_rag_backend.chunking;

import java.util.ArrayList;
import java.util.List;

import com.bharat.scholarship_rag_backend.chunking.MarkdownStructureParser.Block;
import com.bharat.scholarship_rag_backend.chunking.MarkdownStructureParser.Kind;

/**
 * Chunks a FAQ one question-and-answer pair at a time.
 *
 * <p>A pair is the atom here. The corpus breaks pairs in ways a character-based
 * splitter would happily destroy: an answer can start on one page and finish on
 * the next ({@code ishan-uday/faq.md} question 11), a question can be numbered
 * three different ways ({@code ### 4. …}, {@code ### Question 4 …} or a bare
 * {@code 4. …} line), and one answer can be several paragraphs plus bullet and
 * lettered lists. All of that stays in a single chunk, and the page span
 * records both pages.
 *
 * <p>An answer too large for the ceiling is divided into numbered parts, and
 * every part repeats the question. Splitting an answer is acceptable; losing
 * track of which question a passage answered is not.
 */
final class FaqChunker {

    /** Tokens held back for the context header and any continuation marker. */
    private static final int HEADER_RESERVE = 40;

    private final ChunkPacker packer;
    private final TokenCounter counter;

    FaqChunker(TokenCounter counter) {
        this.counter = counter;
        this.packer = new ChunkPacker(counter);
    }

    /** A question and everything that answers it. */
    private record Pair(String question, List<Block> answer, String section) {
    }

    List<TextChunk> chunk(SourceDocument document) {
        List<Block> blocks = MarkdownStructureParser.parse(document.markdown());
        List<Block> preamble = new ArrayList<>();
        List<Pair> pairs = new ArrayList<>();

        String question = null;
        String section = null;
        List<Block> answer = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            String text = block.text();

            if (text == null) {
                continue;
            }
            String trimmed = text.strip();

            if (block.kind() == Kind.HEADING && trimmed.endsWith(":") && startsBulletList(blocks, i)) {
                // "IMPORTANT Notes for the Applicants:" introduces notes that are
                // not part of the preceding answer. Close the pair and carry the
                // heading forward as its own section.
                if (question != null) {
                    pairs.add(new Pair(question, answer, section));
                } else {
                    preamble.add(block);
                }
                question = null;
                answer = new ArrayList<>();
                section = trimmed;
                continue;
            }

            String detected = detectQuestion(block);
            if (detected != null) {
                if (question != null) {
                    pairs.add(new Pair(question, answer, section));
                } else if (!answer.isEmpty()) {
                    preamble.addAll(answer);
                }
                question = detected;
                answer = new ArrayList<>();
                continue;
            }

            if (question == null) {
                preamble.add(block);
            } else {
                answer.add(block);
            }
        }

        if (question != null) {
            pairs.add(new Pair(question, answer, section));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;

        // The blocks before the first question are the FAQ's own front matter.
        // They are packed as a unit; emitting one chunk per line would produce
        // scraps of four tokens each.
        if (!preamble.isEmpty()) {
            String header = contextHeader(document, "About this FAQ");
            int budget = Math.max(1, packer.targetTokens() - HEADER_RESERVE - counter.count(header));

            List<Block> expanded = new ArrayList<>();
            for (Block block : preamble) {
                expanded.addAll(packer.splitOversized(block, budget));
            }

            List<ChunkPacker.Group> groups = packer.pack(expanded, budget, List.of(), true);
            if (groups.isEmpty()) {
                groups = List.of(new ChunkPacker.Group(expanded, null, null));
            }

            for (ChunkPacker.Group group : groups) {
                StringBuilder text = new StringBuilder(header);
                text.append(renderAll(group.blocks()));
                String content = text.toString().strip();

                chunks.add(new TextChunk(
                        document.scheme(), document.documentId(), document.contentType(),
                        document.academicYear(), group.pageStart(), group.pageEnd(),
                        "About this FAQ", null, index++, counter.count(content), content));
            }
        }

        // Adjacent pairs are batched so chunks reach a useful size, but a pair
        // is never divided: each keeps its own "Q:" label inside the chunk.
        int targetCeiling = packer.targetTokens() - HEADER_RESERVE;
        List<Pair> batch = new ArrayList<>();
        int batchTokens = 0;

        for (Pair pair : pairs) {
            String header = contextHeader(document, pair.section());
            int cost = pairTokens(pair) + counter.count(header);

            if (!batch.isEmpty() && batchTokens + cost > targetCeiling) {
                chunks.add(buildPairBatch(document, batch, index++));
                batch = new ArrayList<>();
                batchTokens = 0;
            }

            // A single question-and-answer pair can exceed the ceiling on its
            // own. It cannot stay intact without producing an oversized chunk,
            // so split the answer into parts and repeat the question in every
            // part. The numbering keeps the parts identifiable.
            if (cost > targetCeiling) {
                List<TextChunk> parts = buildSplitPairParts(document, pair, header, index);
                chunks.addAll(parts);
                index += parts.size();
                continue;
            }

            batch.add(pair);
            batchTokens += cost;
        }
        if (!batch.isEmpty()) {
            chunks.add(buildPairBatch(document, batch, index));
        }
        return chunks;
    }

    /**
     * One oversized FAQ pair divided into as many chunks as its answer needs.
     * Every part repeats the question and says which part it is, so a retrieved
     * part remains a self-contained answer rather than a fragment.
     */
    private List<TextChunk> buildSplitPairParts(
            SourceDocument document, Pair pair, String header, int startIndex) {

        int questionCost = counter.count("Q: " + pair.question());
        int partLabelReserve = 24;
        int budget = Math.max(1, packer.targetTokens() - HEADER_RESERVE
                - counter.count(header) - questionCost - partLabelReserve);

        List<Block> expanded = new ArrayList<>();
        for (Block block : pair.answer()) {
            expanded.addAll(packer.splitOversized(block, budget));
        }

        List<ChunkPacker.Group> groups = packer.pack(expanded, budget, List.of(), true);
        if (groups.isEmpty()) {
            groups = List.of(new ChunkPacker.Group(expanded, null, null));
        }

        List<TextChunk> parts = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            ChunkPacker.Group group = groups.get(i);
            StringBuilder text = new StringBuilder(header);
            text.append("Q: ").append(pair.question())
                    .append(" (Part ").append(i + 1).append(" of ")
                    .append(groups.size()).append(")\n");
            text.append(renderAll(group.blocks()));

            String content = text.toString().strip();
            parts.add(new TextChunk(
                    document.scheme(), document.documentId(), document.contentType(),
                    document.academicYear(), group.pageStart(), group.pageEnd(),
                    pair.section(), pair.question(), startIndex + i,
                    counter.count(content), content));
        }
        return parts;
    }

    /** One or more complete question-and-answer pairs rendered as a single chunk. */
    private TextChunk buildPairBatch(SourceDocument document, List<Pair> batch, int index) {
        StringBuilder text = new StringBuilder(
                contextHeader(document, batch.get(0).section()));

        if (batch.size() > 1) {
            text.append("Questions: ");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) {
                    text.append(" | ");
                }
                text.append(batch.get(i).question());
            }
            text.append('\n');
        }

        // Every pair keeps its own question in the body, not just in metadata.
        // A chunk holding an answer with no visible question is unanswerable on
        // its own once it is detached from the FAQ document.
        List<Block> all = new ArrayList<>();
        for (Pair pair : batch) {
            text.append("Q: ").append(pair.question()).append('\n');
            text.append(renderAll(pair.answer())).append("\n\n");
            all.addAll(pair.answer());
        }

        String content = text.toString().strip();
        return new TextChunk(
                document.scheme(), document.documentId(), document.contentType(),
                document.academicYear(),
                firstPage(all),
                lastPage(all),
                batch.get(0).section(),
                batch.size() == 1 ? batch.get(0).question() : batch.size() + " FAQ questions",
                index,
                counter.count(content),
                content);
    }

    private int pairTokens(Pair pair) {
        int total = counter.count(pair.question());
        for (Block block : pair.answer()) {
            total += counter.count(block.text());
        }
        return total;
    }

    /**
     * Recognises a question in any of the shapes the cleaner emits. The answer
     * blocks that follow keep their own wording; only the leading label is
     * dropped from the question line.
     */
    private String detectQuestion(Block block) {
        if (block.question() != null) {
            return block.question();
        }
        if (block.kind() == Kind.SUBHEADING) {
            return MarkdownStructureParser.questionFrom(block.text());
        }
        if (block.kind() == Kind.PARAGRAPH) {
            String text = block.text().strip();
            return text.matches("^\\d{1,2}[.)]\\s+\\S.*\\?$")
                    ? MarkdownStructureParser.questionFrom(text)
                    : null;
        }
        return null;
    }

    private boolean startsBulletList(List<Block> blocks, int index) {
        for (int i = index + 1; i < blocks.size() && i <= index + 2; i++) {
            if (blocks.get(i).kind() == Kind.BULLET) {
                return true;
            }
        }
        return false;
    }

    private String contextHeader(SourceDocument document, String section) {
        String scheme = document.scheme().name().replace('_', ' ');
        StringBuilder header = new StringBuilder(scheme).append(" | FAQ");
        if (section != null && !section.isBlank()) {
            header.append(" | ").append(section.strip());
        }
        return header.append('\n').toString();
    }

    private String renderAll(List<Block> blocks) {
        StringBuilder text = new StringBuilder();
        for (Block block : blocks) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(render(block)).append('\n');
        }
        return text.toString().strip();
    }

    private String render(Block block) {
        return switch (block.kind()) {
            case HEADING -> "## " + block.text();
            case SUBHEADING -> "### " + block.text();
            case BULLET, LIST_ITEM, PARAGRAPH -> block.text();
        };
    }

    private static Integer firstPage(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.page() != null) {
                return block.page();
            }
        }
        return null;
    }

    private static Integer lastPage(List<Block> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i).page() != null) {
                return blocks.get(i).page();
            }
        }
        return null;
    }
}