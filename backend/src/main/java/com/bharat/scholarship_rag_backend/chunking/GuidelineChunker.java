package com.bharat.scholarship_rag_backend.chunking;

import java.util.ArrayList;
import java.util.List;

import com.bharat.scholarship_rag_backend.chunking.MarkdownStructureParser.Block;
import com.bharat.scholarship_rag_backend.chunking.MarkdownStructureParser.Kind;

/**
 * Chunks a guideline by section.
 *
 * <p>Sections are the primary boundary and a heading never travels away from the
 * text it introduces. A section smaller than the token ceiling is not left to
 * become a tiny chunk on its own; adjacent sections are combined until the
 * budget is full, which is what keeps chunks in the useful 400-600 range
 * without ever cutting a section apart. Only a section too large for the
 * ceiling is divided, and it is divided at paragraph and list boundaries.
 *
 * <p>Three shapes in this corpus need explicit handling:
 *
 * <ul>
 *   <li><b>Cover pages.</b> A title page is a run of bodyless headings with a
 *       line or two between them. Left alone, each becomes its own section and
 *       produces a dozen scraps. A heading with no body under it folds into the
 *       pending title instead, and the leftover lines stay in the text.
 *   <li><b>Headings wrapped across two lines.</b> {@code ## 4. PRE-MATRIC …}
 *       followed by {@code ## DISABILITES} is one heading, not two, so the
 *       second folds into the first.
 *   <li><b>Acronym headings inside annexure lists.</b> {@code ## (JBIMS)} is an
 *       institute abbreviation, not a section. Folding it in would corrupt the
 *       real section title, so it is left as an ordinary line.
 * </ul>
 */
final class GuidelineChunker {

    /** Past this length, a folded heading run is dumped to the body instead. */
    private static final int TITLE_FOLD_LIMIT = 80;

    /** Headings listed in a chunk header before it is truncated. */
    private static final int MAX_HEADERS_LISTED = 3;

    /** Tokens held back for the context header and any continuation marker. */
    private static final int HEADER_RESERVE = 40;

    /** Floor for re-packing a section that keeps rendering over the ceiling. */
    private static final int MIN_SPLIT_BUDGET = 120;

    private final ChunkPacker packer;
    private final TokenCounter counter;

    GuidelineChunker(TokenCounter counter) {
        this.counter = counter;
        this.packer = new ChunkPacker(counter);
    }

    /**
     * A titled run of blocks.
     *
     * @param title         heading text, possibly folded from several lines
     * @param headingBlocks the heading blocks themselves, emitted ahead of the
     *                      body so the original structure survives
     * @param body          everything the heading introduces
     */
    private record Section(String title, List<Block> headingBlocks, List<Block> body) {

        int tokens(TokenCounter counter) {
            int total = 0;
            for (Block block : headingBlocks) {
                total += counter.count(block.text());
            }
            for (Block block : body) {
                total += counter.count(block.text());
            }
            return total;
        }
    }

    List<TextChunk> chunk(SourceDocument document) {
        List<Section> sections = splitIntoSections(MarkdownStructureParser.parse(document.markdown()));

        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;

        List<Section> batch = new ArrayList<>();
        int batchTokens = 0;

        for (Section section : sections) {
            // The header is charged per chunk, so its cost is estimated for a
            // single section; a multi-section batch only adds section titles.
            int sectionTokens = section.tokens(counter);
            int withHeader = sectionTokens + counter.count(headerFor(document, List.of(section)));
            int targetCeiling = packer.targetTokens() - HEADER_RESERVE;

            if (sectionTokens > targetCeiling) {
                // Too large to sit beside anything and too large to stay whole.
                if (!batch.isEmpty()) {
                    List<TextChunk> built = buildVerified(document, batch, index);
                    chunks.addAll(built);
                    index += built.size();
                    batch = new ArrayList<>();
                    batchTokens = 0;
                }
                List<TextChunk> parts = splitOversizedSection(document, section, index);
                chunks.addAll(parts);
                index += parts.size();
                continue;
            }

            if (!batch.isEmpty() && batchTokens + withHeader > targetCeiling) {
                List<TextChunk> built = buildVerified(document, batch, index);
                chunks.addAll(built);
                index += built.size();
                batch = new ArrayList<>();
                batchTokens = 0;
            }

            batch.add(section);
            batchTokens += withHeader;
        }

        if (!batch.isEmpty()) {
            chunks.addAll(buildVerified(document, batch, index));
        }
        return chunks;
    }

    /**
     * Builds the chunk and guarantees the ceiling holds.
     *
     * <p>Packing works on raw block text, but a rendered chunk is larger: heading
     * markers and the joins between blocks add tokens that {@code pack} cannot
     * see. Rather than trusting the estimate, the result is measured and a batch
     * that overshoots is halved and rebuilt. A lone section that still overshoots
     * is re-packed with its rendered overhead charged.
     */
    private List<TextChunk> buildVerified(SourceDocument document, List<Section> batch, int startIndex) {
        TextChunk chunk = buildChunk(document, batch, startIndex);

        if (chunk.tokenCount() <= packer.maxTokens()) {
            return List.of(chunk);
        }

        if (batch.size() > 1) {
            int mid = batch.size() / 2;
            List<TextChunk> out = new ArrayList<>(
                    buildVerified(document, batch.subList(0, mid), startIndex));
            out.addAll(buildVerified(document, batch.subList(mid, batch.size()),
                    startIndex + out.size()));
            return out;
        }

        Section section = batch.get(0);
        int budget = packer.maxTokens() - HEADER_RESERVE
                - counter.count(headerFor(document, List.of(section)));

        List<Block> body = new ArrayList<>();
        for (Block block : section.body()) {
            body.addAll(packer.splitOversized(block, budget));
        }
        if (body.isEmpty()) {
            body = section.headingBlocks();
        }

        List<ChunkPacker.Group> groups = packer.pack(body, budget, section.headingBlocks(), true);
        if (groups.size() > 1) {
            return splitOversizedSection(document, section, startIndex);
        }

        // A single unbroken block wider than the ceiling is the only case left.
        // It is emitted as-is and reported rather than mangled further.
        return List.of(chunk);
    }

    /** Keeps small sections whole by batching them up to the ceiling. */
    private TextChunk buildChunk(SourceDocument document, List<Section> batch, int index) {
        List<Block> blocks = new ArrayList<>();
        for (Section section : batch) {
            blocks.addAll(section.headingBlocks());
            blocks.addAll(section.body());
        }

        StringBuilder text = new StringBuilder(headerFor(document, batch));
        appendBody(text, blocks);
        String content = text.toString().strip();

        return new TextChunk(
                document.scheme(),
                document.documentId(),
                document.contentType(),
                document.academicYear(),
                pageStart(blocks),
                pageEnd(blocks),
                batch.get(0).title(),
                batch.size() > 1 ? batch.get(1).title() : null,
                index,
                counter.count(content),
                content);
    }

    /** Divides one oversized section at block boundaries, header intact. */
    private List<TextChunk> splitOversizedSection(SourceDocument document,
                                                  Section section,
                                                  int startIndex) {
        String header = headerFor(document, List.of(section));
        int budget = packer.maxTokens() - HEADER_RESERVE - counter.count(header);

        // Packing sees raw block text, but rendering prefixes every heading with
        // a marker and joins blocks with newlines. A section carrying dozens of
        // annexure headings therefore renders larger than its token estimate, so
        // the parts are built and measured, then re-packed tighter until each one
        // genuinely fits.
        for (int attempt = 0; attempt < 8; attempt++) {
            List<TextChunk> parts = renderParts(document, section, header, budget, startIndex);
            boolean withinCeiling = parts.stream()
                    .allMatch(part -> part.tokenCount() <= packer.maxTokens());

            if (withinCeiling || budget <= MIN_SPLIT_BUDGET) {
                return parts;
            }
            budget = Math.max(MIN_SPLIT_BUDGET, budget - (int) (budget * 0.15));
        }

        return renderParts(document, section, header, MIN_SPLIT_BUDGET, startIndex);
    }

    private List<TextChunk> renderParts(SourceDocument document,
                                         Section section,
                                         String header,
                                         int budget,
                                         int startIndex) {
        List<Block> body = new ArrayList<>();
        for (Block block : section.body()) {
            body.addAll(packer.splitOversized(block, budget));
        }
        if (body.isEmpty()) {
            body = section.headingBlocks();
        }

        List<ChunkPacker.Group> groups = packer.pack(body, budget, section.headingBlocks(), true);
        if (groups.isEmpty()) {
            return List.of(buildChunk(document, List.of(section), 0));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int parts = groups.size();
        for (int i = 0; i < parts; i++) {
            List<Block> blocks = new ArrayList<>(section.headingBlocks());
            blocks.addAll(groups.get(i).blocks());

            StringBuilder text = new StringBuilder(header);
            if (parts > 1) {
                text.append("(section part ").append(i + 1).append(" of ").append(parts).append(")\n");
            }
            appendBody(text, blocks);
            String content = text.toString().strip();

            chunks.add(new TextChunk(
                    document.scheme(),
                    document.documentId(),
                    document.contentType(),
                    document.academicYear(),
                    groups.get(i).pageStart(),
                    groups.get(i).pageEnd(),
                    section.title(),
                    null,
                    startIndex + i,
                    counter.count(content),
                    content));
        }
        return chunks;
    }

    /**
     * Folds the block stream into sections. A heading only opens a new section
     * once the previous one has body; otherwise it extends the pending title.
     */
    private List<Section> splitIntoSections(List<Block> blocks) {
        List<Section> sections = new ArrayList<>();

        StringBuilder title = new StringBuilder();
        List<Block> headingBlocks = new ArrayList<>();
        List<Block> body = new ArrayList<>();

        for (Block block : blocks) {
            boolean sectionHeading = block.kind() == Kind.HEADING && block.sectionBoundary();

            if (sectionHeading) {
                if (!body.isEmpty()) {
                    sections.add(new Section(title.toString(),
                            new ArrayList<>(headingBlocks), new ArrayList<>(body)));
                    reset(title, headingBlocks, body);
                }
                fold(title, headingBlocks, block);
                continue;
            }

            if (block.isHeading()) {
                // A wrapped heading continues the title; an acronym or a short
                // token in an annexure list stays ordinary text.
                if (canExtendTitle(title, headingBlocks, block)) {
                    title.append(' ').append(block.text().strip());
                    headingBlocks.add(block);
                } else {
                    body.add(block);
                }
                continue;
            }

            body.add(block);
        }

        if (!body.isEmpty() || !headingBlocks.isEmpty() || title.length() > 0) {
            sections.add(new Section(title.toString(),
                    new ArrayList<>(headingBlocks), new ArrayList<>(body)));
        }
        return sections;
    }

    private void reset(StringBuilder title, List<Block> headingBlocks, List<Block> body) {
        title.setLength(0);
        headingBlocks.clear();
        body.clear();
    }

    private void fold(StringBuilder title, List<Block> headingBlocks, Block block) {
        title.append(block.text().strip());
        headingBlocks.add(block);
    }

    /**
     * A heading extends the pending title only while the title is still short,
     * the previous heading really was a title rather than annexure noise, and
     * this heading does not look like an abbreviation.
     */
    private boolean canExtendTitle(StringBuilder title, List<Block> headingBlocks, Block block) {
        if (title.length() == 0) {
            return false;
        }
        if (title.length() >= TITLE_FOLD_LIMIT) {
            return false;
        }
        if (isAbbreviationLike(block.text())) {
            return false;
        }
        return !headingBlocksAreAnnexureNoise(headingBlocks);
    }

    /** {@code (JBIMS)}, {@code (NEST)}, {@code (2021)}, {@code (DIVYANGJAN)}. */
    private static boolean isAbbreviationLike(String text) {
        String value = text.strip();
        if (value.startsWith("(") && value.endsWith(")") && value.length() <= 20) {
            return true;
        }
        return value.length() <= 3;
    }

    private static boolean headingBlocksAreAnnexureNoise(List<Block> headingBlocks) {
        for (Block block : headingBlocks) {
            if (!MarkdownStructureParser.isSectionTitle(block.text())) {
                return true;
            }
        }
        return false;
    }

    private String headerFor(SourceDocument document, List<Section> batch) {
        StringBuilder header = new StringBuilder(document.scheme().name().replace('_', ' '))
                .append(" | Scheme Guidelines");

        List<String> titles = new ArrayList<>();
        for (Section section : batch) {
            String value = section.title().strip();
            if (!value.isBlank() && !"Front matter".equals(value)) {
                titles.add(value);
            }
        }

        if (titles.size() == 1) {
            header.append(" | ").append(titles.get(0));
        } else if (titles.size() > 1) {
            header.append(" | Sections: ");
            for (int i = 0; i < Math.min(titles.size(), MAX_HEADERS_LISTED); i++) {
                if (i > 0) {
                    header.append("; ");
                }
                header.append(titles.get(i));
            }
            if (titles.size() > MAX_HEADERS_LISTED) {
                header.append("; …");
            }
        }
        return header.append('\n').toString();
    }

    private void appendBody(StringBuilder target, List<Block> blocks) {
        for (Block block : blocks) {
            String line = render(block);
            if (line.isBlank()) {
                continue;
            }
            if (!target.toString().endsWith("\n")) {
                target.append('\n');
            }
            target.append(line).append('\n');
        }
    }

    private static String render(Block block) {
        return switch (block.kind()) {
            case HEADING -> "## " + block.text();
            case SUBHEADING -> "### " + block.text();
            case BULLET, LIST_ITEM, PARAGRAPH -> block.text();
        };
    }

    private static Integer pageStart(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.page() != null) {
                return block.page();
            }
        }
        return null;
    }

    private static Integer pageEnd(List<Block> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i).page() != null) {
                return blocks.get(i).page();
            }
        }
        return null;
    }
}