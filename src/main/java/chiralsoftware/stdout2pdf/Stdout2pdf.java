package chiralsoftware.stdout2pdf;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Command(name = "Stdout2pdf", mixinStandardHelpOptions = true, version = "1.0",
         description = "Converts ANSI-colored text from stdin or file to PDF directly (no libs).")
public class Stdout2pdf implements Runnable {
    @Parameters(index = "0", arity = "0..1", description = "Input log file (optional; defaults to stdin)")
    private String inputFile;

    @Parameters(index = "1", arity = "1", description = "Output PDF file")
    private String outputFile;

    @Option(names = {"-h", "--header"}, description = "Optional header text for the PDF")
    private String header;

    private static final Map<String, float[]> COLOR_MAP;
    static {
        Map<String, float[]> tempMap = new HashMap<>();
        tempMap.put("30", new float[]{0f, 0f, 0f});       // black
        tempMap.put("31", new float[]{1f, 0f, 0f});       // red
        tempMap.put("32", new float[]{0f, 1f, 0f});       // green
        tempMap.put("33", new float[]{1f, 1f, 0f});       // yellow
        tempMap.put("34", new float[]{0f, 0f, 1f});       // blue
        tempMap.put("35", new float[]{1f, 0f, 1f});       // magenta
        tempMap.put("36", new float[]{0f, 1f, 1f});       // cyan
        tempMap.put("37", new float[]{1f, 1f, 1f});       // white
        COLOR_MAP = Collections.unmodifiableMap(tempMap);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Stdout2pdf()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            final String ansiText = readInput();
            generatePdf(ansiText);
            System.out.println("PDF generated: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private String readInput() throws IOException {
        final StringBuilder sb = new StringBuilder();
        BufferedReader br;
        if (inputFile != null) {
            br = new BufferedReader(new java.io.FileReader(inputFile));
        } else {
            br = new BufferedReader(new InputStreamReader(System.in));
        }
        try {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    private void generatePdf(String ansiText) throws IOException {
        try (OutputStream os = new FileOutputStream(outputFile)) {
            // PDF header
            os.write("%PDF-1.4\n%âãïó\n".getBytes());

            List<Integer> offsets = new ArrayList<>();
            offsets.add(0); // Dummy for index 0

            // Collect content streams for each page
            List<ByteArrayOutputStream> pageContents = new ArrayList<>();
            ByteArrayOutputStream currentContent = new ByteArrayOutputStream();
            int lineCount = (header != null && !header.isEmpty()) ? 2 : 0;
            final int maxLinesPerPage = 70; // Approx for A4 at 8pt with 10 leading

            float[] currentColor = {0f, 0f, 0f}; // Default black

            startNewPage(currentContent);

            Pattern ansiPattern = Pattern.compile("\033\\[([\\d;]+)m");
            Matcher matcher = ansiPattern.matcher(ansiText);
            int lastEnd = 0;

            while (matcher.find()) {
                String textSegment = ansiText.substring(lastEnd, matcher.start());
                lineCount = addWrappedText(currentContent, textSegment, currentColor, lineCount, maxLinesPerPage, pageContents);

                String[] codes = matcher.group(1).split(";");
                if (codes.length > 0 && "0".equals(codes[0])) {
                    currentColor = new float[]{0f, 0f, 0f}; // Reset
                } else {
                    for (String code : codes) {
                        if (COLOR_MAP.containsKey(code)) {
                            currentColor = COLOR_MAP.get(code);
                        }
                    }
                }
                lastEnd = matcher.end();
            }

            String remaining = ansiText.substring(lastEnd);
            lineCount = addWrappedText(currentContent, remaining, currentColor, lineCount, maxLinesPerPage, pageContents);

            if (currentContent.size() > 0) {
                currentContent.write("\nET\n".getBytes());
                pageContents.add(currentContent);
            }

            // Write objects
            // Object 1: Catalog
            ByteArrayOutputStream catalog = new ByteArrayOutputStream();
            catalog.write("<< /Type /Catalog /Pages 2 0 R >>".getBytes());
            writeObject(os, 1, catalog.toByteArray(), offsets);

            // Object 2: Pages
            ByteArrayOutputStream pages = new ByteArrayOutputStream();
            pages.write(("<< /Type /Pages /Count " + pageContents.size() + " /Kids [").getBytes());
            for (int i = 0; i < pageContents.size(); i++) {
                pages.write(( (3 + i * 2) + " 0 R ").getBytes());
            }
            pages.write("] >>".getBytes());
            writeObject(os, 2, pages.toByteArray(), offsets);

            int objNum = 3;
            int fontRef = pageContents.size() * 2 + 3; // Font after all pages/contents
            int boldFontRef = (header != null && !header.isEmpty()) ? fontRef + 1 : 0; // Bold font only if header is present
            for (ByteArrayOutputStream pageContent : pageContents) {
                // Page object
                ByteArrayOutputStream page = new ByteArrayOutputStream();
                String resources = (boldFontRef != 0) ? " /Font << /F1 " + fontRef + " 0 R /F2 " + boldFontRef + " 0 R >> " : " /Font << /F1 " + fontRef + " 0 R >> ";
                page.write(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents " + (objNum + 1) + " 0 R /Resources <<" + resources + ">> >>").getBytes());
                writeObject(os, objNum, page.toByteArray(), offsets);
                objNum++;

                // Content object
                ByteArrayOutputStream contentObj = new ByteArrayOutputStream();
                contentObj.write(("<< /Length " + pageContent.size() + " >>\nstream\n").getBytes());
                contentObj.write(pageContent.toByteArray());
                contentObj.write("\nendstream".getBytes());
                writeObject(os, objNum, contentObj.toByteArray(), offsets);
                objNum++;
            }

            // Font object (Courier)
            ByteArrayOutputStream font = new ByteArrayOutputStream();
            font.write("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>".getBytes());
            writeObject(os, objNum, font.toByteArray(), offsets);
            objNum++;

            // Bold Font object (Courier-Bold) only if header is present
            if (boldFontRef != 0) {
                ByteArrayOutputStream boldFont = new ByteArrayOutputStream();
                boldFont.write("<< /Type /Font /Subtype /Type1 /BaseFont /Courier-Bold >>".getBytes());
                writeObject(os, objNum, boldFont.toByteArray(), offsets);
            }

            // xref
            int xrefOffset = (int) ((FileOutputStream) os).getChannel().position();
            os.write(("xref\n0 " + offsets.size() + "\n0000000000 65535 f \n").getBytes());
            for (int i = 1; i < offsets.size(); i++) {
                os.write(String.format("%010d 00000 n \n", offsets.get(i)).getBytes());
            }

            // Trailer
            os.write(("trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >> \nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes());
        }
    }

    private void startNewPage(ByteArrayOutputStream currentContent) throws IOException {
        currentContent.write("BT\n".getBytes());

        if (header != null && !header.isEmpty()) {
            currentContent.write("/F2 12 Tf\n12 TL\n".getBytes());

            float pageWidth = 612f;
            float fontSize = 12f;
            float charWidth = 0.6f * fontSize; // Approximate for Courier-Bold
            float textWidth = header.length() * charWidth;
            float xOffset = (pageWidth - textWidth) / 2f;

            currentContent.write(String.format("%.0f 750 Td\n0 0 0 rg\n(%s) Tj\nT*\nT*\n", xOffset, escapeString(header)).getBytes());

            float dx = 40 - xOffset;
            currentContent.write(String.format("%.0f 0 Td\n", dx).getBytes());
        } else {
            currentContent.write("/F1 8 Tf\n10 TL\n40 750 Td\n".getBytes());
        }

        currentContent.write("/F1 8 Tf\n10 TL\n".getBytes()); // Ensure for body
    }

    private static void writeObject(OutputStream os, int objNum, byte[] content, List<Integer> offsets) throws IOException {
        offsets.add((int) ((FileOutputStream) os).getChannel().position());
        os.write((objNum + " 0 obj\n").getBytes());
        os.write(content);
        os.write("\nendobj\n".getBytes());
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private int addWrappedText(ByteArrayOutputStream currentContent, String text, float[] rgb, int lineCount, int maxLinesPerPage, List<ByteArrayOutputStream> pageContents) throws IOException {
        final int maxCharsPerLine = 80; // Approx for Courier at 8pt

        final String[] lines = text.split("\n");
        for (String line : lines) {
            int start = 0;
            while (start < line.length()) {
                if (lineCount >= maxLinesPerPage) {
                    currentContent.write("\nET\n".getBytes());
                    pageContents.add(currentContent);
                    currentContent = new ByteArrayOutputStream();
                    startNewPage(currentContent);
                    lineCount = (header != null && !header.isEmpty()) ? 2 : 0; // Account for header lines
                }

                int end = Math.min(start + maxCharsPerLine, line.length());
                String sub = line.substring(start, end);
                String display = sub;
                if (end < line.length()) {
                    display = sub.substring(0, sub.length() - 3) + "...";
                }
                currentContent.write(String.format("%.1f %.1f %.1f rg\n(%s) Tj\nT*\n", rgb[0], rgb[1], rgb[2], escapeString(display)).getBytes());
                lineCount++;
                start = end;
            }
        }
        return lineCount;
    }
}