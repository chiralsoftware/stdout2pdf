package chiralsoftware.stdout2pdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import static java.lang.System.exit;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.compile;

public final class checkit {
    
    private static String abbreviate(String s) {
        if(s == null) throw new NullPointerException("null string");
        if(s.length() < 70) return s;
        return s.substring(0,70) + "...";
    }
    
    private static void usage() {
            out.println("usage:");
            out.println("   checkit --present <message if lines present> --empty <message if lines empty>");
            out.println("substitutions:");
            out.println("{lines} number of lines in input");
            out.println("{input} the input");
            exit(0);
    }

    private static String removeTrailingNewlines(String input) {
        int endIndex = input.length();

        while (endIndex > 0 && input.charAt(endIndex - 1) == '\n') {
            endIndex--;
        }

        return input.substring(0, endIndex);
    }
   
    public static void main(String[] args) throws Exception {
        if(args.length < 4) usage();

        String emptyMessage = null;
        String presentMessage = null;
        String filePath = null;
        String grepPattern = null;

        for(int i = 0; i < args.length; i++) {
            switch(args[i]) {
                case "--empty": emptyMessage = args[++i]; break;
                case "--present": presentMessage = args[++i]; break;
                case "--grep": grepPattern = args[++i]; break;
                case "--file": filePath = args[++i]; break;
                default: usage();
            }
        }
        if(emptyMessage == null || presentMessage == null) usage();

        final File file;
        if(filePath == null) {
            file = null;
        } else {
            file = new File(filePath);
            if(! file.exists()) {
                out.println("file: " + file + " not found");
                exit(1);
            }
            if(! file.canRead()) {
                out.println("file: " + file + " can't read");
                exit(1);
            }
            if(file.isDirectory()) {
                out.println("file: " + file + " is a directory");
                exit(1);
            }
        }
        final BufferedReader br =
                new BufferedReader(new InputStreamReader(file == null ? System.in : new FileInputStream(file), UTF_8));
        final StringBuilder result = new StringBuilder();
        int lines = 0;
        String line;
        final Pattern grep = grepPattern == null ? null : compile(grepPattern);
        while((line = br.readLine()) != null) {
            if (grep != null && !grep.matcher(line).find()) continue;
            if(lines <= 5) result.append(abbreviate(line)).append("\n");
            lines++;
        }
        final String message = lines == 0 ? emptyMessage : presentMessage;
        out.println(message.replace("{lines}", Integer.toString(lines)).
                replace("{input}", removeTrailingNewlines(result.toString())));
        exit(lines == 0 ? 0 : 1);
    }
    
}
