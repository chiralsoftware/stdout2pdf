package chiralsoftware.stdout2pdf;

import static chiralsoftware.stdout2pdf.Color.BLACK;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import static chiralsoftware.stdout2pdf.Color.RED;
import static java.util.Collections.unmodifiableList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.stream.Collectors.joining;

/**
 *
 */
public class PageMaker {

    private static final int lineLength = 80;
    private static final int tabWidth = 8;
    
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]");

    private static int lengthOfLineOfChunks(List<Chunk> chunks) {
        int result = 0;
        for(Chunk c : chunks) result += c.length();
        return result;
    }
    
    private static String fixTabs(String input, int offset) {
        final StringBuilder sb = new StringBuilder();
        for(int i = 0; i < input.length(); i++ ) {
            final char c = input.charAt(i);
            if(c == '\t') {
                final int spacesToAdd = tabWidth - (i + offset) % tabWidth;
                for(int x = 0; x < spacesToAdd; x++) sb.append(" ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private static int counter = 0;
    /** Take one line, which could be long, and turn it into tokens, dealing
     with tabs */
    static List<Token> stringToTokens(String s) {
            final Matcher matcher = ANSI_PATTERN.matcher(s);
            int lastEnd = 0;
            final List<Token> result = new ArrayList<>();
            int offset = 0;

            while (matcher.find()) {
                // Add the text before the delimiter
                final String fixed = fixTabs(s.substring(lastEnd, matcher.start()), offset);
                if(! fixed.isEmpty()) result.add(new StringToken(fixed));

                offset += fixed.length();
                // Add the captured delimiter
                final String matchedAnsi = matcher.group(0);
                if(isVisibleAnsi(matchedAnsi)) result.add(new ColorToken(matchedAnsi));
                lastEnd = matcher.end();
            }
            // Add any remaining text after the last delimiter
            final String remainder = s.substring(lastEnd);
            if(! remainder.isEmpty()) {
                final StringToken remainderToken = new StringToken(fixTabs(remainder, offset));

                result.add(remainderToken);
            }
            counter++;

            return result;
    }

    /** This should pick out ANSI codes which will be ignored - essentially cursor movement codes */
    private static boolean isVisibleAnsi(String ansiString) {
        return ! ansiString.substring(ansiString.indexOf("[")).equals("[K");
    }

    /** Take a single input string, which is one line, and split to possibly
     multiple lines. */
    private static List<Line> makeLines(TextContext context, String s) {
        if(s == null) throw new NullPointerException("Can't proccess null string");
        final List<Line> result = new ArrayList<>();

        final List<Token> tokens = new ArrayList<>();
        tokens.addAll(stringToTokens(s));
   
        List<Chunk> lineOfChunks = new ArrayList<>();
        while(! tokens.isEmpty()) {
//            String str = tokens.removeFirst();
            Token t = tokens.removeFirst();
            if(! t.visible()) {
                // we simply set the context and continue along...
                if(t instanceof ColorToken ct) {
                    context.setColor(ct.color());
                }
                continue;
            }
            if(t.length() == 0) continue;
            if(t.length() + lengthOfLineOfChunks(lineOfChunks) <= lineLength) { // this string is the last chunk 
                lineOfChunks.add(new Chunk(context.getColor(),((StringToken) t).string())); // this line is done
                continue;
            }
            // we have overflow
            final int currentColumn = lengthOfLineOfChunks(lineOfChunks);
            final String subString = ((StringToken) t).string().substring(0, lineLength - currentColumn);
            lineOfChunks.add(new Chunk(context.getColor(), subString));
            result.add(new Line(lineOfChunks, true));
            lineOfChunks = new ArrayList<>();
            tokens.addFirst(((StringToken) t).substring(subString.length()));
        }
        result.add(new Line(lineOfChunks, false));
        return result;
    }

    /** Fully read a set of lines from a BufferedReader and make a list of Line objects */
    static List<Line> makeLines(BufferedReader br) throws Exception {
        String line;
        final List<Line> result = new ArrayList<>();
        final TextContext textContext = new TextContext();
        textContext.setColor(BLACK);
        while((line = br.readLine()) != null) {
            final List<Line> lines = makeLines(textContext, line); 
            result.addAll(lines);
        }
        return unmodifiableList(result);
        
    }
    
    public static void main(String[] args) throws Exception {
        final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        final List<Line> lines = makeLines(br);
        int i = 0;
            for(Line l : lines) {
                System.out.println(l.chunks().stream().map(Chunk::text).collect(joining("")));
        }
    }
}
