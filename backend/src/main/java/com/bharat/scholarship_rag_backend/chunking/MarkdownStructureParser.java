package com.bharat.scholarship_rag_backend.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns cleaned Markdown into an ordered list of {@link Block}s that respect
 * the document's real structure.
 *
 * <p>Three properties of this corpus drive the design, all of them verified
 * against the source files rather than assumed:
 *
 * <ul>
 *   <li><b>Numbers become headings.</b> The cleaner promotes a line to
 *       {@code ##} fairly eagerly, so in the institute annexures PIN codes and
 *       slot counts surface as headings — {@code top-class-pwd/guideline.md}
 *       alone has about sixty of them. Treating every {@code ##} as a section
 *       would shred the annexure into meaningless fragments, so a heading whose
 *       characters are mostly digits is demoted back to running text.
 *
 *   <li><b>Headings get split across lines.</b> Long headings wrap, giving
 *       {@code ## 4. PRE-MATRIC ...} followed by {@code ## DISABILITES}. These
 *       are consecutive headings with no body between them, so they are folded
 *       into one title.
 *
 *   <li><b>Sentences straddle page breaks.</b> A paragraph can stop mid
 *       sentence at the bottom of a page and resume on the next. Only
 *       high-confidence joins are made — a dangling function word, an
 *       unbalanced bracket, or a following line that starts lower-case — so no
 *       text is invented and no real paragraph boundary is crossed.
 * </ul>
 *
 * <p>Nothing here summarises, rewrites or drops source wording. The only edits
 * are rejoining a sentence the PDF split, and treating a numeric pseudo-heading
 * as the ordinary text it is.
 */
final class MarkdownStructureParser {

    private static final Pattern PAGE_MARKER = Pattern.compile("^---\\s*Page\\s+(\\d+)\\s*---$");

    /** Heading split across lines; the cleaner emits these at level 3. */
    private static final Pattern SUBHEADING = Pattern.compile("^###\\s+(.*)$");

    private static final Pattern BULLET = Pattern.compile("^[-\\u00b7\\u2022\\u2023\\u25cf\\u25aa]\\s+.*");

    /** {@code (i)} {@code a)} {@code iv.} {@code 3)} — kept verbatim as list items. */
    private static final Pattern LIST_ITEM = Pattern.compile(
            "^(?:\\([a-z0-9ivxIVX]+\\)|[a-z][.)]|[ivxIVX]{1,4}[.)]|\\d{1,2}[.)])\\s+.*");

    /** Horizontal rules and ornamental asterisk runs carry no meaning. */
    private static final Pattern DECORATIVE = Pattern.compile("^[\\s*=#_-]{3,}$");

    /** A numbered question that the cleaner left as plain text, e.g. {@code 3. Can I ...?}. */
    private static final Pattern INLINE_QUESTION = Pattern.compile("^\\d{1,2}[.)]\\s+\\S.*\\?\\s*$");

    /**
     * Words that cannot legally end an English sentence. A paragraph ending in
     * one of these was cut off by a page break, not by the author.
     */
    private static final Pattern DANGLING_WORD = Pattern.compile(
            "(?i)\\b(the|a|an|of|and|or|in|to|for|with|by|from|at|on|as|that|which|is|are|was|were|be|"
                    + "its|their|his|her|per|under|into|over|such|than|between|within|after|before|"
                    + "during|through|against|about|upon|shall|will|shall not|not)$");

    /** Marks where an FAQ answer begins, tolerating the leading page-header noise. */
    static final Pattern ANSWER_MARKER = Pattern.compile(
            "(?i)\\b(answer|reply)\\b\\s*:?\\s*$");

    private MarkdownStructureParser() {
    }

    enum Kind {
        HEADING,
        SUBHEADING,
        PARAGRAPH,
        BULLET,
        LIST_ITEM
    }

    /**
     * One structural unit.
     *
     * @param kind            what sort of unit this is
     * @param text            content with Markdown prefixes already stripped
     * @param page            page the unit starts on
     * @param sectionBoundary true when this unit opens a new section, which is
     *                        what the guideline chunker splits on
     * @param question        for FAQ question units, the question text
     */
    record Block(Kind kind, String text, Integer page, boolean sectionBoundary, String question) {

        boolean isHeading() {
            return kind == Kind.HEADING || kind == Kind.SUBHEADING;
        }

        boolean isListLike() {
            return kind == Kind.BULLET || kind == Kind.LIST_ITEM;
        }
    }

    static List<Block> parse(String markdown) {
        String[] lines = markdown.split("\r?\n", -1);
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        Integer paragraphPage = null;
        Integer currentPage = null;

        for (String raw : lines) {
            String line = raw.strip();

            Matcher page = PAGE_MARKER.matcher(line);
            if (page.matches()) {
                flush(blocks, paragraph, paragraphPage);
                currentPage = Integer.valueOf(page.group(1));
                continue;
            }

            if (line.isEmpty() || DECORATIVE.matcher(line).matches()) {
                flush(blocks, paragraph, paragraphPage);
                continue;
            }

            if (line.startsWith("## ") && !line.startsWith("### ")) {
                flush(blocks, paragraph, paragraphPage);
                String title = line.substring(3).strip();
                // A mostly-numeric "heading" is a PIN code or slot count that the
                // cleaner over-promoted. Keep the text, drop the hierarchy.
                if (isNumericNoise(title)) {
                    blocks.add(new Block(Kind.PARAGRAPH, title, currentPage, false, null));
                } else {
                    blocks.add(new Block(Kind.HEADING, title, currentPage, isSectionTitle(title), null));
                }
                continue;
            }

            Matcher sub = SUBHEADING.matcher(line);
            if (sub.matches()) {
                flush(blocks, paragraph, paragraphPage);
                String title = sub.group(1).strip();
                blocks.add(new Block(Kind.SUBHEADING, title, currentPage, false, questionFrom(title)));
                continue;
            }

            if (BULLET.matcher(line).matches()) {
                flush(blocks, paragraph, paragraphPage);
                blocks.add(new Block(Kind.BULLET, normaliseBullet(line), currentPage, false, null));
                continue;
            }

            if (LIST_ITEM.matcher(line).matches()) {
                flush(blocks, paragraph, paragraphPage);
                blocks.add(new Block(Kind.LIST_ITEM, line, currentPage, false, null));
                continue;
            }

            if (paragraph.isEmpty()) {
                paragraphPage = currentPage;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }

        flush(blocks, paragraph, paragraphPage);
        return repairWrappedSentences(blocks);
    }

    /**
     * Rejoins sentences the page layout cut in half. Only applied when the
     * evidence is unambiguous, because a wrong join silently rewrites meaning.
     */
    private static List<Block> repairWrappedSentences(List<Block> blocks) {
        List<Block> repaired = new ArrayList<>(blocks.size());

        for (Block block : blocks) {
            Block previous = repaired.isEmpty() ? null : repaired.get(repaired.size() - 1);

            if (previous != null
                    && canBeContinuation(previous, block)
                    && shouldJoin(previous, block)) {
                repaired.remove(repaired.size() - 1);
                String joined = previous.text() + " " + block.text().strip();
                repaired.add(new Block(previous.kind(), joined.strip(),
                        previous.page(), previous.sectionBoundary(), previous.question()));
                continue;
            }
            repaired.add(block);
        }
        return repaired;
    }

    /** Only prose paragraphs wrap; never a heading, list item or bullet. */
    private static boolean canBeContinuation(Block previous, Block next) {
        if (previous.kind() != Kind.PARAGRAPH || next.kind() != Kind.PARAGRAPH) {
            return false;
        }
        // A numbered FAQ question opens new ground, so nothing is joined onto it.
        return next.question() == null && previous.question() == null;
    }

    private static boolean shouldJoin(Block previous, Block next) {
        String tail = previous.text().strip();
        if (tail.isEmpty()) {
            return false;
        }

        char last = tail.charAt(tail.length() - 1);
        // A closed bracket or a full stop means the author finished the thought.
        if (last == '.' || last == '!' || last == '?' || last == ';' || last == '"') {
            return false;
        }

        // An unclosed bracket cannot be the end of a sentence: "… (DBT)" with
        // one paren open is a wrap, not a closing paren.
        if (unbalancedBrackets(tail)) {
            return true;
        }

        String nextText = next.text().strip();
        if (!nextText.isEmpty() && Character.isLowerCase(nextText.charAt(0))) {
            return true;
        }

        return DANGLING_WORD.matcher(tail).find();
    }

    private static boolean unbalancedBrackets(String text) {
        int open = 0;
        int close = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                close++;
            }
        }
        return open > close;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph, Integer page) {
        if (paragraph.isEmpty()) {
            return;
        }
        String text = paragraph.toString().strip();
        paragraph.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        blocks.add(new Block(Kind.PARAGRAPH, text, page, false,
                INLINE_QUESTION.matcher(text).matches() ? stripNumbering(text) : null));
    }

    /**
     * Extracts the question from an FAQ heading in any of the three forms the
     * corpus uses: {@code ### 4. Who is eligible?}, {@code ### Question 4 For
     * which class…} and the bare numbering the cleaner sometimes leaves behind.
     */
    static String questionFrom(String title) {
        String text = title.strip();
        if (text.isEmpty() || text.endsWith(":") || !text.contains("?")) {
            return null;
        }
        return stripNumbering(text);
    }

    private static String stripNumbering(String text) {
        String stripped = text.strip();
        Matcher questionWord = Pattern
                .compile("(?i)^question\\s+\\d+\\s*[.:)]?\\s*")
                .matcher(stripped);
        if (questionWord.find()) {
            stripped = stripped.substring(questionWord.end()).strip();
        }
        Matcher number = Pattern.compile("^\\d{1,2}\\s*[.)]\\s*").matcher(stripped);
        if (number.find()) {
            stripped = stripped.substring(number.end()).strip();
        }
        return stripped;
    }

    private static String normaliseBullet(String line) {
        return "- " + line.replaceFirst("^[-\\u00b7\\u2022\\u2023\\u25cf\\u25aa]\\s+", "");
    }

    /**
     * True when a heading is really a number that escaped promotion. The
     * annexures are full of them: {@code ## 221005 16}, {@code ## 191132 J&K 3},
     * {@code ## 382424}.
     */
    private static boolean isNumericNoise(String title) {
        int digits = 0;
        int letters = 0;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isLetter(c)) {
                letters++;
            }
        }
        if (letters == 0) {
            return true;
        }
        return digits > letters;
    }

    /**
     * Whether a promoted heading actually names a section. Requires either an
     * explicit number ({@code 2. ELIGIBILITY}) or a multi-word title, so that
     * single tokens like {@code ## FOR}, {@code ## (NEST)} and {@code ## CONTENT}
     * stay as running text instead of inventing a section per line.
     */
    static boolean isSectionTitle(String title) {
        String text = title.strip();
        if (text.isEmpty() || isNumericNoise(text)) {
            return false;
        }
        if (Pattern.compile("^\\d{1,2}\\s*[.)]\\s+\\S").matcher(text).find()) {
            return true;
        }

        String[] words = text.split("\\s+");
        if (words.length < 2) {
            return false;
        }
        if (words.length > 12 || text.length() > 120) {
            return false;
        }

        int titleish = 0;
        for (String word : words) {
            String cleaned = word.replaceAll("[^A-Za-z]", "");
            if (cleaned.length() >= 3) {
                titleish++;
            }
        }
        return titleish >= 2;
    }

    /**
     * Recovers the academic year a document speaks for. Uses the most common
     * {@code 2025-26}-style range in the document, falling back to a bare year
     * near the top. Returns null rather than guessing when neither is present.
     */
    static String academicYear(String markdown) {
        MapCounter years = new MapCounter();
        Matcher range = Pattern.compile("\\b(20\\d{2}\\s*-\\s*20\\d{2})\\b").matcher(markdown);
        while (range.find()) {
            years.add(range.group(1).replaceAll("\\s", ""));
        }
        if (!years.isEmpty()) {
            return years.mostCommon();
        }

        String[] lines = markdown.split("\r?\n", -1);
        int inspected = 0;
        for (String line : lines) {
            Matcher single = Pattern.compile("\\b(20\\d{2})\\b").matcher(line);
            if (single.find() && inspected < 30) {
                return single.group(1);
            }
            inspected++;
            if (inspected >= 30) {
                break;
            }
        }
        return null;
    }

    private static final class MapCounter {

        private final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        private final List<String> order = new ArrayList<>();

        void add(String value) {
            counts.merge(value, 1, Integer::sum);
            if (!order.contains(value)) {
                order.add(value);
            }
        }

        boolean isEmpty() {
            return counts.isEmpty();
        }

        String mostCommon() {
            String best = null;
            int bestCount = -1;
            for (String value : order) {
                int count = counts.get(value);
                if (count > bestCount) {
                    bestCount = count;
                    best = value;
                }
            }
            return best;
        }
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}