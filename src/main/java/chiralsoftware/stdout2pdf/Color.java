package chiralsoftware.stdout2pdf;

/**
 *
 */
final record Color(float r, float g, float b) {
    
    static final Color BLACK = new Color(0,0,0);
    static final Color RED = new Color(1,0,0);
    static final Color GREEN  = new Color(0, 1, 0);
    static final Color BLUE = new Color(0, 0, 1);
    static final Color YELLOW = new Color(0.8f,0.8f,0);
    
}
