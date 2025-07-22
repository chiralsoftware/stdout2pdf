package chiralsoftware.stdout2pdf;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import static java.lang.System.err;
import static java.lang.System.exit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Convert ANSI text input to PostScript output.
 * This needs a reflect-config.json file to compile to native:
 [
 {
 "name": "chiralsoftware.stdout2pdf.Stdout2Ps",
 "allDeclaredConstructors": true,
 "allPublicConstructors": true,
 "allDeclaredMethods": true,
 "allPublicMethods": true,
 "allDeclaredFields": true,
 "allPublicFields": true
 },
 {
 "name": "picocli.CommandLine$AutoHelpMixin",
 "allDeclaredConstructors": true,
 "allPublicConstructors": true,
 "allDeclaredMethods": true,
 "allPublicMethods": true,
 "allDeclaredFields": true,
 "allPublicFields": true
 }
 ]

 *
 */
@Command(name = "Stdout2ps", mixinStandardHelpOptions = true, version = "1.0",
         description = "Converts ANSI-colored text from stdin or file to PostScript")
public final class Stdout2Ps implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Input file (optional; defaults to stdin)")
    public String inputFile;

    @Parameters(index = "1", arity = "0..1", description = "Output PDF file (optiona; defaults to stdout)")
    public String outputFile;

    @Option(names = {"-h", "--header"}, description = "Optional header text for the PDF")
    public String header;
    
    private static final int linesPerPage = 55;
    
     static String escapePostscriptString(String text) {
        if (text == null || text.isEmpty()) return "";

        final StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '(' -> escaped.append("\\(");
                case ')' -> escaped.append("\\)");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\f' -> // Form feed
                    escaped.append("\\f");
                case '\b' -> // Backspace
                    escaped.append("\\b");
                default -> // If it's a regular character, just append it
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private void outputPage(OutputStream os, List<Line> allLines, int startLine, int endLine, int pageNumber) throws IOException {
        // start the page
        os.write(("%%Page: " + pageNumber + " " + pageNumber + "\n"
                + "newpath\n").getBytes());
        if(header != null && ! header.isBlank()) {
            os.write(("gsave\n"
                    + "/Helvetica-Bold findfont 14 scalefont setfont\n"
                    + "/headertext (" + escapePostscriptString(header) + ") def\n"
                            + "currentpagedevice /PageSize get aload pop  % Puts [width height] array on stack, then width and height\n" +
                            "/PageHeight exch def                     % Pop height and define PageHeight\n" +
                            "/PageWidth exch def  "
                            + "headertext stringwidth pop\n"
                            + "PageWidth exch sub 2 div\n"
                            + "PageHeight 60 sub moveto\n"
                            + "headertext show\n"
                            + "grestore\n").getBytes());
        }
        os.write(("72 700 moveto\n").getBytes());
        for(int lineNumber = startLine; lineNumber < endLine; lineNumber++) {
            final Line line = allLines.get(lineNumber);
            for(Chunk c : line.chunks()) {
                final Color color = c.color();
                os.write((color.r() + " " + color.g() + " " + color.b() + " setrgbcolor\n").getBytes());
                os.write(("(" + escapePostscriptString(c.text()) + ") show\n").getBytes());
            }
            if(line.overflow()) {
                os.write(("0 0 0 setrgbcolor\n"
                        + "( ...) show\n").getBytes());
                
            }
            os.write(("72 " + (700 - (lineNumber - startLine + 1) * 12) + " moveto\n").getBytes());
        }
        os.write("showpage\n".getBytes());
    }

    private void generatePs(List<Line> allLines) throws IOException {
        if(allLines == null) {
            err.println("null argument");
            return;
        }
        final OutputStream os =
                outputFile == null ? System.out : new FileOutputStream(outputFile);
        os.write("%!PS-Adobe-3.0\n".getBytes());
        os.write(("% Define a procedure to move to the next line\n" +
            "% Assumes a line spacing of 1.2 times the font size\n" +
            "/nextline {\n" +
            "    currentpoint pop           % Get current X, discard Y\n" +
            "    -14 rmoveto                % Move down 14 points (adjust as needed)\n" +
            "} def\n"
            + "/Courier findfont 9 scalefont setfont\n").getBytes());


        if(allLines.isEmpty()) {
            err.println("No lines read");
        }

        final int numberOfFullPages = allLines.size() / linesPerPage;

        // output all the full pages first. This could be zero full pages
        for(int pageNumber = 0; pageNumber < numberOfFullPages; pageNumber++) {
            outputPage(os, allLines, pageNumber * linesPerPage,
                    pageNumber * linesPerPage + linesPerPage, pageNumber + 1);

        }
        // if there is a partial page output that too
        if(allLines.size() % linesPerPage != 0) {
            outputPage(os, allLines, numberOfFullPages * linesPerPage, allLines.size(), numberOfFullPages + 1);
        }

        // Trailer
        os.write(("%%EOF\n").getBytes());
    }

    private List<Line> readInput() throws IOException {
        final BufferedReader br;
        List<Line> allLines = null;
        if (inputFile != null) {
            br = new BufferedReader(new FileReader(inputFile));
        } else {
            br = new BufferedReader(new InputStreamReader(System.in,  StandardCharsets.UTF_8));
        }
        try {
            allLines = PageMaker.makeLines(br);
        } catch(Exception ie) {
            err.println("caught: " + ie.getMessage());
        } finally {
            br.close();
        }
        return allLines;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Stdout2Ps()).execute(args);
        exit(exitCode);
    }

    @Override
    public void run() {
        try {
            final List<Line> allLines = readInput();
            generatePs(allLines);
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
        }
    }


}
