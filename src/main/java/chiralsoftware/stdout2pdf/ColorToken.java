package chiralsoftware.stdout2pdf;

import static chiralsoftware.stdout2pdf.Color.BLACK;
import static chiralsoftware.stdout2pdf.Color.BLUE;
import static chiralsoftware.stdout2pdf.Color.GREEN;
import static chiralsoftware.stdout2pdf.Color.RED;
import static chiralsoftware.stdout2pdf.Color.YELLOW;

record ColorToken(Color color) implements Token {
    static final String ANSI_RESET = "\u001B[0m";
    static final String ANSI_RED = "\u001B[0;31m";
    static final String ANSI_GREEN = "\u001B[0;32m";
    static final String ANSI_BLUE = "\u001B[0;34m";
    static final String ANSI_YELLOW = "\u001B[1;33m";

    ColorToken(String s) {
        this(switch(s) {
        case ANSI_RED -> RED;
        case ANSI_GREEN -> GREEN;
        case ANSI_BLUE -> BLUE;
        case ANSI_YELLOW -> YELLOW;
        default -> BLACK; // this will cover reset
    }
    );
    }


@Override
    public boolean visible() { return false; }
@Override
    public int length() { return 0; }
    
}
