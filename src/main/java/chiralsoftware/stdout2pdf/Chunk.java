package chiralsoftware.stdout2pdf;

/**
 */
record Chunk(Color color, String text) {
    
    int length() { return text.length(); }
    
}
