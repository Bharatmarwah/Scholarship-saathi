package com.bharat.scholarship_rag_backend.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Single-step ingestion pipeline: converts every scholarship PDF directly into
 * clean, structured Markdown, mirroring the source tree under the
 * cleaned-markdown directory, ready for section-aware chunking.
 *
 * <p>Source: {@code src/main/resources/scholarships/**&#47;*.pdf}
 * <br>Output: mirrors the source tree under {@code src/main/resources/cleaned-markdown/}
 *
 * <p>Extraction and cleaning happen in memory in one pass; no intermediate
 * {@code .txt} files are written. Raw text is pulled page by page with page
 * markers, then cleaned conservatively. Cleaning removes ONLY clear
 * extraction/formatting noise and NEVER changes or drops meaningful content:
 * <ul>
 *   <li>blank lines and runs of blank lines,</li>
 *   <li>standalone page footers such as {@code Page 3 of 60},</li>
 *   <li>consecutive identical lines produced by overlapping text extraction,</li>
 *   <li>excess whitespace (leading/trailing and runs of spaces/tabs).</li>
 * </ul>
 *
 * <p>Everything else is preserved verbatim in the original order. Structure is
 * added for Markdown, based only on the document's own layout cues:
 * <ul>
 *   <li>section headings become {@code ## } headings,</li>
 *   <li>FAQ questions ({@code Question N} / numbered lines ending in '?')
 *       become {@code ### } headings,</li>
 *   <li>numbered, lettered, roman or parenthesised list items, bullets and
 *       annexure category rows stay as separate list lines,</li>
 *   <li>lines broken only by PDF layout are joined back into single
 *       paragraphs; separate paragraphs, sections, list items and pages are
 *       never merged.</li>
 * </ul>
 *
 * <p>The {@code --- Page N ---} markers added during extraction are kept
 * verbatim (each as its own Markdown paragraph) so page boundaries remain
 * traceable.
 */
public final class PdfToMarkdown {

    private static final String RESOURCES_DIR = "src/main/resources";
    private static final String SOURCE_DIR = RESOURCES_DIR + "/scholarships";
    private static final String OUTPUT_DIR = RESOURCES_DIR + "/cleaned-markdown";
    private static final String PDF_EXTENSION = ".pdf";
    private static final String MARKDOWN_EXTENSION = ".md";

    /** Page markers added by the extractor, e.g. {@code --- Page 7 ---}. */
    private static final Pattern PAGE_MARKER =
            Pattern.compile("^---\\s*Page\\s+\\d+\\s*---$");

    /** Page footer noise produced by the PDF layout, e.g. {@code Page 3 of 60}. */
    private static final Pattern PAGE_FOOTER =
            Pattern.compile("^Page\\s+\\d+\\s+of\\s+\\d+$", Pattern.CASE_INSENSITIVE);

    /** Numbered headings/sections, e.g. {@code 1. INTRODUCTION}. */
    private static final Pattern NUMBERED = Pattern.compile("^\\d{1,2}[.)]\\s+");

    /** Table rows where the number is not followed by a period, e.g. {@code 1 Indian Institute}. */
    private static final Pattern NUMBERED_SPACE = Pattern.compile("^\\d{1,2}\\s+[A-Z]");

    /** FAQ format {@code Question 5 How can I ...?}. */
    private static final Pattern FAQ_PREFIX = Pattern.compile("^Question\\s+\\d+", Pattern.CASE_INSENSITIVE);

    /** Numbered question format ending in '?', e.g. {@code 1. What is ...?}. */
    private static final Pattern FAQ_NUMBERED = Pattern.compile("^\\d{1,2}[.)]\\s+.+[?]$");

    /** Letter list items, e.g. {@code a. The scheme ...}. */
    private static final Pattern LETTER_ITEM = Pattern.compile("^[a-z][.)]\\s+[A-Z\\d]");

    /** Roman list items, e.g. {@code i. / ii. / iv.}. */
    private static final Pattern ROMAN_ITEM = Pattern.compile("^[ivxlcdm]{1,4}[.)]\\s+");

    /** Parenthesised items, e.g. {@code (I) / (a) / (vi)}. */
    private static final Pattern PAREN_ITEM =
            Pattern.compile("^\\([a-zivxlcdm]+\\)\\s*\\S", Pattern.CASE_INSENSITIVE);

    /** Annexure category rows, e.g. {@code I-IITs[16]}, {@code II-NITs[30]}. */
    private static final Pattern CATEGORY_ROW = Pattern.compile("^[IVX]+[-\\u2013\\u2014]");

    /** Leading bullet glyphs produced by the extractor. */
    private static final Pattern BULLET =
            Pattern.compile("^[\\u00b7\\u2022\\uf0b7\\u2023\\u25cf\\u25aa]+\\s*");

    /** All-caps line used as an unnumbered heading, e.g. {@code ANNEXURES}. */
    private static final Pattern ALL_CAPS_HEADING = Pattern.compile("^[A-Z0-9 &(),:/]{2,50}$");

    /** Printer separators / decorative text, e.g. {@code **********}. */
    private static final Pattern DECORATIVE = Pattern.compile("^[\\s*#=_\\-]{3,}$|\\*{4,}");

    /** Annexure table headings such as {@code Annexure-1} or {@code Annexure -I}. */
    private static final Pattern ANNEXURE_START =
            Pattern.compile("^(?i)annexure\\s*[-\\u2013\\u2014]?\\s*[ivxlcdm\\d]");

    /** Page marker rule (see class javadoc). */
    private enum Type { PAGE, HEADING, FAQ, LIST_ITEM, DECORATIVE, PLAIN }

    private static final class Parsed {
        final Type type;
        final String text;

        Parsed(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public static void main(String[] args) throws IOException {
        List<Path> outputs = convertAll();

        System.out.println("Converted " + outputs.size() + " PDF(s) to Markdown:");
        outputs.forEach(path -> System.out.println("  " + path));
    }

    /**
     * Converts every scholarship PDF directly into clean Markdown under the
     * cleaned-markdown directory. Returns the list of written files.
     */
    public static List<Path> convertAll() throws IOException {
        List<Path> outputs = new ArrayList<>();

        for (Path source : findSourcePdfs()) {
            Path output = outputPathFor(source);

            String cleaned = pdfToMarkdown(source);

            Files.createDirectories(output.getParent());
            Files.writeString(output, cleaned, StandardCharsets.UTF_8);

            outputs.add(output);
        }

        return outputs;
    }

    /** Extracts a PDF page by page (tagging pages) and cleans it to Markdown. */
    public static String pdfToMarkdown(Path pdfPath) throws IOException {
        return cleanText(extractWithPageMarkers(pdfPath));
    }

    /** Extracts a PDF's text page by page, tagging each page: {@code --- Page N ---}. */
    public static String extractWithPageMarkers(Path pdfPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(pdfPath)) {
            try (PDDocument document = loadDocument(inputStream)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                StringBuilder result = new StringBuilder();
                int totalPages = document.getNumberOfPages();

                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    result.append("--- Page ").append(page).append(" ---\n");
                    result.append(stripper.getText(document));
                    result.append("\n");
                }

                return result.toString();
            }
        }
    }

    private static PDDocument loadDocument(InputStream inputStream) throws IOException {
        return Loader.loadPDF(inputStream.readAllBytes());
    }

    /**
     * Cleans raw extracted text into structured Markdown. One conservative
     * pass: normalises whitespace, drops blank lines, page footers and
     * consecutive duplicate lines, then rebuilds the document as Markdown
     * blocks, joining only lines broken by PDF layout.
     */
    public static String cleanText(String raw) {
        String[] rawLines = raw.split("\\r?\\n");

        List<String> keptLines = new ArrayList<>();
        String previousKept = null;

        for (String rawLine : rawLines) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (PAGE_FOOTER.matcher(line).matches()) {
                continue;
            }
            if (line.equals(previousKept)) {
                continue;
            }

            keptLines.add(line);
            previousKept = line;
        }

        return renderMarkdown(keptLines);
    }

    // ------------------------------------------------------- private helpers

    private static String normalizeLine(String rawLine) {
        String line = rawLine.replace('\u00a0', ' ').strip();
        if (line.isEmpty()) {
            return "";
        }
        return line.replaceAll("[\\t ]+", " ");
    }

    private static boolean endsWithSentence(char c) {
        return c == '.' || c == '?' || c == '!';
    }

    private static boolean startsWithUpperOrDigit(String s) {
        char c = s.charAt(0);
        return Character.isUpperCase(c) || Character.isDigit(c);
    }

    /** True when a new paragraph should start instead of merging into the previous one. */
    private static boolean shouldBreakParagraph(String paragraph, String nextLine) {
        char last = paragraph.charAt(paragraph.length() - 1);
        return endsWithSentence(last) && startsWithUpperOrDigit(nextLine);
    }

    private static String joinSpacer(String paragraph) {
        char last = paragraph.charAt(paragraph.length() - 1);
        if (last == '-' && paragraph.length() < 2) {
            return "";
        }
        return " ";
    }

    /** Converts leading bullet glyphs to the Markdown {@code - } list prefix. */
    private static String toBullet(String line) {
        Matcher m = BULLET.matcher(line);
        if (!m.find()) {
            return line;
        }
        String rest = line.substring(m.end()).strip();
        if (rest.isEmpty()) {
            return line;
        }
        return "- " + rest;
    }

    /** Classifies a single (already normalised) line. */
    private static Parsed parse(String rawLine) {
        if (PAGE_MARKER.matcher(rawLine).matches()) {
            return new Parsed(Type.PAGE, rawLine);
        }

        String line = toBullet(rawLine);
        if (!line.equals(rawLine) || line.startsWith("- ")) {
            return new Parsed(Type.LIST_ITEM, line);
        }

        if (FAQ_PREFIX.matcher(line).find() || FAQ_NUMBERED.matcher(line).find()) {
            return new Parsed(Type.FAQ, line);
        }

        if (looksLikeSectionHeading(line) || looksLikeAllCapsHeading(line)) {
            return new Parsed(Type.HEADING, line);
        }

        if (looksLikeListItem(line)) {
            return new Parsed(Type.LIST_ITEM, line);
        }

        if (DECORATIVE.matcher(line).find()) {
            return new Parsed(Type.DECORATIVE, line);
        }

        return new Parsed(Type.PLAIN, line);
    }

    /**
     * Numbered section headings such as {@code 1. INTRODUCTION} or
     * {@code 4. Eligibility for Scholarship}. A line is a heading only when it
     * is short, starts with a number, its first word begins with a capital and
     * it does not read as a running sentence.
     */
    private static boolean looksLikeSectionHeading(String line) {
        Matcher m = NUMBERED.matcher(line);
        if (!m.find()) {
            return false;
        }
        String rest = line.substring(m.end()).strip();
        if (rest.length() < 3 || rest.length() > 100) {
            return false;
        }
        char last = rest.charAt(rest.length() - 1);
        if (endsWithSentence(last) || last == ',') {
            return false;
        }
        int colon = rest.indexOf(':');
        if (colon >= 0) {
            if (colon > 45) {
                return false;
            }
            String title = rest.substring(0, colon).strip();
            return title.length() >= 3 && Character.isUpperCase(title.charAt(0));
        }
        if (rest.length() > 45 && containsLowerCase(rest)) {
            return false;
        }
        String firstWord = firstWord(rest);
        if (firstWord.isEmpty() || Character.isDigit(firstWord.charAt(0))) {
            return false;
        }
        return Character.isUpperCase(firstWord.charAt(0));
    }

    /** Unnumbered all-caps headings such as {@code CONTENT}, {@code ANNEXURES}. */
    private static boolean looksLikeAllCapsHeading(String line) {
        if (!ALL_CAPS_HEADING.matcher(line).find()) {
            return false;
        }
        char last = line.charAt(line.length() - 1);
        return last != '.' && last != ',';
    }

    private static boolean looksLikeListItem(String line) {
        if (LETTER_ITEM.matcher(line).find()
                || ROMAN_ITEM.matcher(line).find()
                || PAREN_ITEM.matcher(line).find()
                || CATEGORY_ROW.matcher(line).find()) {
            return true;
        }
        Matcher m = NUMBERED.matcher(line);
        if (m.find()) {
            // Questions ("1. What ...") are paragraphs, not list items.
            return !isInterrogativeRest(line, m.end());
        }
        // Table rows such as "1 Indian Institute of Technology ..."
        return NUMBERED_SPACE.matcher(line).find() && wordCount(line) <= 7;
    }

    private static boolean isInterrogativeRest(String line, int from) {
        String rest = line.substring(from).strip();
        String w = firstWord(rest).toLowerCase().replace(".", "").replace(",", "").strip();
        return switch (w) {
            case "what", "which", "who", "whom", "whose", "how", "when", "where", "why",
                    "can", "could", "do", "does", "did", "is", "am", "are", "was", "were",
                    "shall", "should", "will", "would", "may", "might", "must", "whether", "if",
                    "have", "has", "had" -> true;
            default -> false;
        };
    }

    /**
     * Long mixed-case numbered lines that failed the heading test (e.g.
     * {@code 7. Rate and Duration of Scholarship (from financial year 2022-23)}).
     * These keep their own paragraph and must not swallow the following section
     * body.
     */
    private static boolean expandsLongRest(String line) {
        Matcher m = NUMBERED.matcher(line);
        if (!m.find()) {
            return false;
        }
        String rest = line.substring(m.end()).strip();
        return rest.length() > 45 && containsLowerCase(rest);
    }

    private static String firstWord(String s) {
        int end = s.indexOf(' ');
        return end < 0 ? s : s.substring(0, end);
    }

    private static boolean containsLowerCase(String s) {
        for (int k = 0; k < s.length(); k++) {
            if (Character.isLowerCase(s.charAt(k))) {
                return true;
            }
        }
        return false;
    }

    private static int wordCount(String s) {
        int count = 0;
        for (String w : s.strip().split("\\s+")) {
            if (!w.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Answer lines, e.g. {@code Answer:} / {@code Reply:}, that end an FAQ question. */
    private static boolean startsAnswer(String line) {
        return Pattern.compile("^(?i)(answer|reply)\\b").matcher(line).find();
    }

    /**
     * Emits a section heading. When a heading carries an inline body behind a
     * short colon title (e.g. {@code 3. TYPE OF INSTITUTION: The ...}), the
     * title is the {@code ## } heading and the rest is kept as its own
     * paragraph, so no content is ever dropped.
     */
    private static void emitHeading(String text, List<String> blocks) {
        int colon = text.indexOf(':');
        if (colon > 0) {
            String title = text.substring(0, colon + 1).strip();
            if (title.length() <= 46) {
                blocks.add("## " + title);
                String rest = text.substring(colon + 1).strip();
                if (!rest.isEmpty()) {
                    blocks.add(rest);
                }
                return;
            }
        }
        blocks.add("## " + text);
    }

    /**
     * Rebuilds the kept lines as Markdown. A soft paragraph (no document cue)
     * is merged from consecutive plain lines, but only when those lines are
     * visibly continuations (no sentence boundary between them). List items
     * absorb their wrapped continuation lines. The {@code --- Page N ---}
     * markers always end the run, so joined content never crosses a page.
     */
    private static String renderMarkdown(List<String> lines) {
        List<String> blocks = new ArrayList<>();
        boolean inTable = false;

        int i = 0;
        while (i < lines.size()) {
            if (!inTable && ANNEXURE_START.matcher(lines.get(i)).find()) {
                inTable = true;
            }
            Parsed first = parse(lines.get(i));

            switch (first.type) {
                case PAGE, DECORATIVE -> {
                    blocks.add(first.text);
                    i++;
                }
                case HEADING -> {
                    if (inTable && NUMBERED.matcher(first.text).find()) {
                        blocks.add(first.text);
                    } else {
                        emitHeading(first.text, blocks);
                    }
                    i++;
                }
                case FAQ -> {
                    StringBuilder question = new StringBuilder(first.text);
                    int absorbed = 0;
                    while (!question.toString().endsWith("?") && absorbed < 4 && i + 1 < lines.size()) {
                        Parsed next = parse(lines.get(i + 1));
                        if (next.type != Type.PLAIN || startsAnswer(next.text)) {
                            break;
                        }
                        question.append(' ').append(next.text);
                        absorbed++;
                        i++;
                    }
                    blocks.add("### " + question);
                    i++;
                }
                case LIST_ITEM -> {
                    if (!inTable && expandsLongRest(first.text)) {
                        blocks.add(first.text);
                        i++;
                        continue;
                    }
                    StringBuilder item = new StringBuilder(first.text);
                    int j = i + 1;
                    while (j < lines.size()) {
                        Parsed next = parse(lines.get(j));
                        if (next.type != Type.PLAIN || NUMBERED.matcher(next.text).find()) {
                            break;
                        }
                        item.append(joinSpacer(item.toString())).append(next.text);
                        j++;
                    }
                    blocks.add(item.toString());
                    i = j;
                }
                default -> { // plain paragraph, downgraded to a heading if it reads as one
                    StringBuilder paragraph = new StringBuilder(first.text);
                    int j = i + 1;
                    while (j < lines.size()) {
                        Parsed next = parse(lines.get(j));
                        if (next.type != Type.PLAIN) {
                            break;
                        }
                        if (shouldBreakParagraph(paragraph.toString(), next.text)) {
                            break;
                        }
                        paragraph.append(joinSpacer(paragraph.toString())).append(next.text);
                        j++;
                    }
                    String text = paragraph.toString();
                    if (FAQ_NUMBERED.matcher(text).find()) {
                        blocks.add("### " + text);
                    } else if (!inTable && looksLikeSectionHeading(text)) {
                        emitHeading(text, blocks);
                    } else {
                        blocks.add(text);
                    }
                    i = j;
                }
            }
        }

        if (blocks.isEmpty()) {
            return "";
        }
        return String.join("\n\n", blocks) + "\n";
    }

    /** Locates the scholarships directory from the current working directory. */
    private static List<Path> findSourcePdfs() throws IOException {
        Path sourceRoot = resolveSourceRoot();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Scholarships directory not found: " + sourceRoot.toAbsolutePath());
        }

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(PDF_EXTENSION))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /**
     * Resolves src/main/resources/scholarships regardless of whether the
     * app is started from the repo root or from the module directory.
     */
    private static Path resolveSourceRoot() {
        Path workingDir = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                workingDir.resolve(SOURCE_DIR),
                workingDir.resolve("scholarship-rag-backend/" + SOURCE_DIR)
        );

        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(workingDir.resolve(SOURCE_DIR));
    }

    /** Maps a source PDF to its mirrored cleaned Markdown output path. */
    private static Path outputPathFor(Path pdfPath) {
        Path sourceRoot = resolveSourceRoot();
        Path relative = sourceRoot.relativize(pdfPath.toAbsolutePath());

        String relativeStr = relative.toString();
        if (relativeStr.toLowerCase().endsWith(PDF_EXTENSION)) {
            relativeStr = relativeStr.substring(0, relativeStr.length() - PDF_EXTENSION.length())
                    + MARKDOWN_EXTENSION;
        }

        return Path.of("").toAbsolutePath().resolve(OUTPUT_DIR).resolve(relativeStr);
    }
}