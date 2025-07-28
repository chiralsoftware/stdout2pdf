package chiralsoftware.stdout2pdf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;

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
   
    public static void main(String[] args) throws Exception {
        if(args.length != 4) usage();
        String emptyMessage = null;
        String presentMessage = null;
        for(int i = 0; i < args.length; i++) {
            if(args[i].equalsIgnoreCase("--empty")) {
                emptyMessage = args[i + 1];
                i++;
            } else if(args[i].equalsIgnoreCase("--present")) {
                presentMessage = args[i + 1];
                i++;
            }
        }
        if(emptyMessage == null || presentMessage == null) usage();
        final BufferedReader br = new BufferedReader(new InputStreamReader(System.in, UTF_8));
        final StringBuilder result = new StringBuilder();
        int lines = 0;
        String line;
        while((line = br.readLine()) != null) {
            if(lines <= 5) result.append(abbreviate(line)).append("\n");
            lines++;
        }
        final String message = lines == 0 ? emptyMessage : presentMessage;
        out.println(message.replace("{lines}", Integer.toString(lines)).replace("{input}", result));
        exit(lines == 0 ? 0 : 1);
    }
    
}
