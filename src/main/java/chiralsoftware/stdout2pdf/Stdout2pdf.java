package chiralsoftware.stdout2pdf;


import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Chunk;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "stdout-to-pdf", description = "Converts stdin (with ANSI colors) to a PDF")
public class Stdout2pdf implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, description = "Output PDF file (default: stdout)")
    private String outputFile;

    @Option(names = {"-p", "--page-size"}, description = "Page size (e.g., LETTER, A4)", defaultValue = "LETTER")
    private String pageSizeStr;

    @Option(names = {"-s", "--font-size"}, description = "Font size", defaultValue = "10")
    private float fontSize;

    @Option(names = {"-h", "--header"}, description = "Header text to add at the top")
    private String header;

    @Option(names = {"-m", "--margin"}, description = "Margin size in points (applies to all sides)", defaultValue = "20")
    private float margin;

    private static final Color[] NORMAL_COLORS = {
            new Color(0, 0, 0),      // black
            new Color(205, 0, 0),    // red
            new Color(0, 205, 0),    // green
            new Color(205, 205, 0),  // yellow
            new Color(0, 0, 205),    // blue
            new Color(205, 0, 205),  // magenta
            new Color(0, 205, 205),  // cyan
            new Color(229, 229, 229) // white
    };

    private static final Color[] BRIGHT_COLORS = {
            new Color(127, 127, 127), // gray
            new Color(255, 0, 0),     // bright red
            new Color(0, 255, 0),     // bright green
            new Color(255, 255, 0),   // bright yellow
            new Color(0, 0, 255),     // bright blue
            new Color(255, 0, 255),   // bright magenta
            new Color(0, 255, 255),   // bright cyan
            new Color(255, 255, 255)  // bright white
    };

    private static final String CONTINUATION = "...";

    private Color currentFg = NORMAL_COLORS[0];
    private Color currentBg = null;
    private int currentStyle = Font.NORMAL;

    @Override
    public Integer call() throws Exception {
        final Document document = new Document(getPageSize(pageSizeStr));
        final OutputStream outStream = (outputFile == null) ? System.out : new FileOutputStream(outputFile);
        PdfWriter.getInstance(document, outStream);
        document.setMargins(margin, margin, margin, margin);
        document.open();

        final float availableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
        final Font baseCourierFont = new Font(Font.COURIER, fontSize, Font.NORMAL);
        final BaseFont baseCourierBaseFont = baseCourierFont.getCalculatedBaseFont(true);
        final float continuationWidth = baseCourierBaseFont.getWidthPoint(CONTINUATION, fontSize);

        if (header != null) {
            final Font headerFont = new Font(Font.TIMES_ROMAN, 14, Font.BOLD);
            final Paragraph headerParagraph = new Paragraph(header, headerFont);
            headerParagraph.setAlignment(Element.ALIGN_CENTER);
            headerParagraph.setSpacingAfter(10f);
            document.add(headerParagraph);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final List<Chunk> lineChunks = parseLineToChunks(line);
                if (lineChunks.isEmpty()) {
                    final Paragraph emptyParagraph = new Paragraph();
                    emptyParagraph.setLeading(fontSize * 1.2f);
                    document.add(emptyParagraph);
                    continue;
                }

                final List<Chunk> currentLineChunks = new ArrayList<>();
                float currentLineWidth = 0f;

                for (int chunkIndex = 0; chunkIndex < lineChunks.size(); chunkIndex++) {
                    final Chunk originalChunk = lineChunks.get(chunkIndex);
                    final Font font = originalChunk.getFont();
                    final BaseFont baseFont = font.getCalculatedBaseFont(true);
                    final Map attrs = originalChunk.getChunkAttributes();
                    Color bg = null;
                    if (attrs != null) {
                        final Object[] back = (Object[]) attrs.get(Chunk.BACKGROUND);
                        if (back != null) {
                            bg = (Color) back[0];
                        }
                    }
                    final String text = originalChunk.getContent();
                    int start = 0;
                    for (int pos = 0; pos < text.length(); pos++) {
                        final String ch = text.substring(pos, pos + 1);
                        final float w = baseFont.getWidthPoint(ch, fontSize);
                        final boolean hasMore = (pos + 1 < text.length()) || (chunkIndex + 1 < lineChunks.size());
                        final float extra = hasMore ? continuationWidth : 0f;
                        if (currentLineWidth + w > availableWidth - extra) {
                            // Add subchunk from start to pos
                            if (pos > start) {
                                final Chunk subChunk = new Chunk(text.substring(start, pos), font);
                                if (bg != null) {
                                    subChunk.setBackground(bg);
                                }
                                currentLineChunks.add(subChunk);
                            }
                            // Add continuation if hasMore
                            if (hasMore) {
                                final Font continuationFont = new Font(Font.COURIER, fontSize, currentStyle, currentFg);
                                final Chunk continuationChunk = new Chunk(CONTINUATION, continuationFont);
                                if (bg != null) {
                                    continuationChunk.setBackground(bg);
                                }
                                currentLineChunks.add(continuationChunk);
                            }
                            // Add the visual line paragraph
                            final Paragraph visualParagraph = new Paragraph();
                            visualParagraph.setLeading(fontSize * 1.2f);
                            for (final Chunk chnk : currentLineChunks) {
                                visualParagraph.add(chnk);
                            }
                            document.add(visualParagraph);
                            // Reset for next visual line
                            currentLineChunks.clear();
                            currentLineWidth = 0f;
                            start = pos;
                            pos--;  // Adjust to reprocess the current position after ++
                        } else {
                            currentLineWidth += w;
                        }
                    }
                    // Add remaining subchunk after the loop
                    if (start < text.length()) {
                        final Chunk subChunk = new Chunk(text.substring(start), font);
                        if (bg != null) {
                            subChunk.setBackground(bg);
                        }
                        currentLineChunks.add(subChunk);
                    }
                }
                // Add the last visual line without continuation
                if (!currentLineChunks.isEmpty()) {
                    final Paragraph visualParagraph = new Paragraph();
                    visualParagraph.setLeading(fontSize * 1.2f);
                    for (final Chunk chnk : currentLineChunks) {
                        visualParagraph.add(chnk);
                    }
                    document.add(visualParagraph);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading input", e);
        }

        document.close();
        return 0;
    }

    private List<Chunk> parseLineToChunks(final String line) {
        final List<Chunk> chunks = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            final char ch = line.charAt(i);
            if (ch == '\u001B' && i + 1 < line.length() && line.charAt(i + 1) == '[') {
                // Add any accumulated text before the escape sequence
                if (sb.length() > 0) {
                    addChunkToList(chunks, sb.toString());
                    sb.setLength(0);
                }
                // Parse the ANSI sequence
                int j = i + 2;
                final StringBuilder codeBuilder = new StringBuilder();
                while (j < line.length() && line.charAt(j) != 'm') {
                    codeBuilder.append(line.charAt(j));
                    j++;
                }
                if (j < line.length() && line.charAt(j) == 'm') {
                    final String[] codes = codeBuilder.toString().split(";");
                    for (final String codeStr : codes) {
                        if (codeStr.isEmpty()) continue;
                        try {
                            final int code = Integer.parseInt(codeStr);
                            processAnsiCode(code);
                        } catch (NumberFormatException ignored) {
                            // Ignore invalid codes
                        }
                    }
                    i = j + 1;
                } else {
                    // Invalid sequence, treat as literal
                    sb.append(ch);
                    i++;
                }
            } else {
                sb.append(ch);
                i++;
            }
        }
        // Add any remaining text
        if (sb.length() > 0) {
            addChunkToList(chunks, sb.toString());
        }
        return chunks;
    }

    private void addChunkToList(final List<Chunk> chunks, final String text) {
        final Font font = new Font(Font.COURIER, fontSize, currentStyle, currentFg);
        final Chunk chunk = new Chunk(text, font);
        if (currentBg != null) {
            chunk.setBackground(currentBg);
        }
        chunks.add(chunk);
    }

    private void processAnsiCode(final int code) {
        if (code == 0) {
            // Reset
            currentFg = NORMAL_COLORS[0];
            currentBg = null;
            currentStyle = Font.NORMAL;
        } else if (code == 1) {
            // Bold
            currentStyle |= Font.BOLD;
        } else if (code == 3) {
            // Italic
            currentStyle |= Font.ITALIC;
        } else if (code == 4) {
            // Underline
            currentStyle |= Font.UNDERLINE;
        } else if (code == 22) {
            // Normal intensity (not bold)
            currentStyle &= ~Font.BOLD;
        } else if (code == 23) {
            // No italic
            currentStyle &= ~Font.ITALIC;
        } else if (code == 24) {
            // No underline
            currentStyle &= ~Font.UNDERLINE;
        } else if (code >= 30 && code <= 37) {
            // Foreground normal
            currentFg = NORMAL_COLORS[code - 30];
        } else if (code >= 40 && code <= 47) {
            // Background normal
            currentBg = NORMAL_COLORS[code - 40];
        } else if (code >= 90 && code <= 97) {
            // Foreground bright
            currentFg = BRIGHT_COLORS[code - 90];
        } else if (code >= 100 && code <= 107) {
            // Background bright
            currentBg = BRIGHT_COLORS[code - 100];
        } else if (code == 39) {
            // Default foreground
            currentFg = NORMAL_COLORS[0];
        } else if (code == 49) {
            // Default background
            currentBg = null;
        }
        // Ignore other codes
    }

    private Rectangle getPageSize(final String size) {
        switch (size.toUpperCase()) {
            case "LETTER":
                return PageSize.LETTER;
            case "A4":
                return PageSize.A4;
            // Add more sizes as needed
            default:
                throw new IllegalArgumentException("Invalid page size: " + size);
        }
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Stdout2pdf()).execute(args);
        System.exit(exitCode);
    }
}
