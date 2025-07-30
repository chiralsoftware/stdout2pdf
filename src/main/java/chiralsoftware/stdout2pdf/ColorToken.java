package chiralsoftware.stdout2pdf;

import static chiralsoftware.stdout2pdf.Color.BLACK;
import static chiralsoftware.stdout2pdf.Color.BLUE;
import static chiralsoftware.stdout2pdf.Color.GREEN;
import static chiralsoftware.stdout2pdf.Color.RED;
import static chiralsoftware.stdout2pdf.Color.YELLOW;

record ColorToken(Color color) implements Token {
    static final String ANSI_RESET = "\u001B[0m";
    static final String ANSI_DELETE_TO_EOL = "\u001B[K";
    static final String ANSI_RED_RESET = "\u001B[0;31m";
    static final String ANSI_GREEN_RESET = "\u001B[0;32m";
    static final String ANSI_BLUE_RESET = "\u001B[0;34m";
    static final String ANSI_YELLOW_RESET = "\u001B[1;33m";

    static final String ANSI_RED = "\u001B[31m";
    static final String ANSI_RED_BOLD = "\u001B[01;31m";
    static final String ANSI_GREEN = "\u001B[32m";
    static final String ANSI_BLUE = "\u001B[34m";
    static final String ANSI_YELLOW = "\u001B[33m";

    /** this should be fixed to parse the token completely and pull
     * out info on both the font and color. It may need to emit two
     * tokens if there is a font and color in one input string. */
    ColorToken(String s) {
        this(switch(s) {
                    case ANSI_RED_RESET, ANSI_RED, ANSI_RED_BOLD -> RED;
                    case ANSI_GREEN_RESET,ANSI_GREEN -> GREEN;
                    case ANSI_BLUE_RESET, ANSI_BLUE -> BLUE;
                    case ANSI_YELLOW_RESET, ANSI_YELLOW -> YELLOW;
        default -> BLACK; // this will cover reset
    }
    );
    }


@Override
    public boolean visible() { return false; }
@Override
    public int length() { return 0; }
    
}
