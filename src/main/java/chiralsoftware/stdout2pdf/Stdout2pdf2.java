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
import java.util.Collections;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import static java.lang.System.err;

@Command(name = "Stdout2pdf", mixinStandardHelpOptions = true, version = "1.0",
         description = "Converts ANSI-colored text from stdin or file to PDF directly (no libs).")
public class Stdout2pdf2 implements Runnable {
    @Parameters(index = "0", arity = "0..1", description = "Input log file (optional; defaults to stdin)")
    private String inputFile;

    @Parameters(index = "1", arity = "1", description = "Output PDF file")
    private String outputFile;

    @Option(names = {"-h", "--header"}, description = "Optional header text for the PDF")
    private String header;
    
    private static final int linesPerPage = 55;

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
        int exitCode = new CommandLine(new Stdout2pdf2()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            final List<Line> allLines = readInput();
            generatePdf(allLines);
            System.out.println("PDF generated: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private List<Line> readInput() throws IOException {
        final BufferedReader br;
        List<Line> allLines = null;
        if (inputFile != null) {
            br = new BufferedReader(new FileReader(inputFile));
        } else {
            br = new BufferedReader(new InputStreamReader(System.in));
        }
        try {
            allLines = PageMaker.makeLines(br);
        } catch(Exception ie) {
            System.err.println("caught: " + ie.getMessage());
        } finally {
            br.close();
        }
        return allLines;
    }
    
    private static String abbreviate(String s) {
        if(s.length() <= 50) return s;
        return s.substring(0,50) + " ...";
    }
    
    private void outputPage(List<ByteArrayOutputStream> pageContents,
            List<Line> allLines, int start, int end) throws IOException {
        final ByteArrayOutputStream currentContent = new ByteArrayOutputStream();
        startNewPage(currentContent);
        for(int i = start; i < end; i++) {
            addLine(currentContent, allLines.get(i), pageContents);
        }
        currentContent.write("\nET\n".getBytes());
        pageContents.add(currentContent);
    }

    private void generatePdf(List<Line> allLines) throws IOException {
        if(allLines == null) {
            err.println("null argument");
            return;
        }
        try (OutputStream os = new FileOutputStream(outputFile)) {
            // PDF header
            os.write("%PDF-1.4\n".getBytes());
            os.write(new byte[] { '%', (byte)226, (byte)227, (byte)239, (byte)243, '\n' });

            List<Integer> offsets = new ArrayList<>();
            offsets.add(0); // Dummy for index 0

            // Collect content streams for each page
            List<ByteArrayOutputStream> pageContents = new ArrayList<>();

            if(allLines.isEmpty()) {
                err.println("No lines read");
            }

            final int numberOfFullPages = allLines.size() / linesPerPage;
            
            // output all the full pages first. This could be zero full pages
            for(int pageNumber = 0; pageNumber < numberOfFullPages; pageNumber++) {
                outputPage(pageContents, allLines, pageNumber * linesPerPage, 
                        pageNumber * linesPerPage + linesPerPage);
                
            }
            // if there is a partial page output that too
            if(allLines.size() % linesPerPage != 0) {
                outputPage(pageContents, allLines, numberOfFullPages * linesPerPage, allLines.size());
            }
    
            // Write objects
            // Object 1: Catalog
            final ByteArrayOutputStream catalog = new ByteArrayOutputStream();
            catalog.write("<< /Type /Catalog /Pages 2 0 R >>".getBytes());
            writeObject(os, 1, catalog.toByteArray(), offsets);

            // Object 2: Pages
            final ByteArrayOutputStream pages = new ByteArrayOutputStream();
            pages.write(("<< /Type /Pages /Count " + pageContents.size() + " /Kids [").getBytes());
            for (int i = 0; i < pageContents.size(); i++) {
                pages.write(( (3 + i * 2) + " 0 R ").getBytes());
            }
            pages.write("] >>".getBytes());
            writeObject(os, 2, pages.toByteArray(), offsets);

            int objNum = 3;
            
            final int fontRef = pageContents.size() * 2 + 3; // Font after all pages/contents
            for (ByteArrayOutputStream pageContent : pageContents) {
                // Page object
                final ByteArrayOutputStream page = new ByteArrayOutputStream();
                page.write(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents " + (objNum + 1) + " 0 R /Resources << /Font << /F1 " + fontRef + " 0 R >> >> >>").getBytes());
                writeObject(os, objNum, page.toByteArray(), offsets);
                objNum++;

                // Content object
                final ByteArrayOutputStream contentObj = new ByteArrayOutputStream();
                contentObj.write(("<< /Length " + pageContent.size() + " >>\nstream\n").getBytes());
                contentObj.write(pageContent.toByteArray());
                contentObj.write("\nendstream".getBytes());
                writeObject(os, objNum, contentObj.toByteArray(), offsets);
                objNum++;
            }

            // Font object
            final ByteArrayOutputStream font = new ByteArrayOutputStream();
            font.write("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>".getBytes());
            writeObject(os, objNum, font.toByteArray(), offsets);

            // xref
            final int xrefOffset = (int) ((FileOutputStream) os).getChannel().position();
            os.write(("xref\n0 " + offsets.size() + "\n0000000000 65535 f \n").getBytes());
            for (int i = 1; i < offsets.size(); i++) {
                os.write(String.format("%010d 00000 n \n", offsets.get(i)).getBytes());
            }

            // Trailer
            os.write(("trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >> \nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes());
        }
    }
    

    private void startNewPage(ByteArrayOutputStream currentContent) throws IOException {
        currentContent.write((
                "BT\n"
                + "/F1 8 Tf\n"
                + "40 750 Td\n"
                + "10 TL\n").
                getBytes());

        if (header != null && !header.isEmpty()) {
            currentContent.write("0 0 0 rg\n".getBytes());
            currentContent.write(("(" + escapeString(header) + ") Tj\nT*\nT*\n").getBytes());
        }
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

    private int addLine(ByteArrayOutputStream currentContent,  
        Line line, List<ByteArrayOutputStream> pageContents) throws IOException {
        final List<Chunk> chunks = line.chunks();
        for(Chunk c : chunks) {
            final Color color = c.color();
            currentContent.write(String.format("%.1f %.1f %.1f rg\n(%s) Tj\n", 
                    color.r(), color.g(), color.b(), 
                    escapeString(c.text())).getBytes());
        }
        currentContent.write("T*\n".getBytes());
        return 0;
    }
}
