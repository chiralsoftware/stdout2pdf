package chiralsoftware.stdout2pdf;

final class TextContext {
    
    private Color color;

    Color getColor() {
        return color;
    }

    void setColor(Color color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "TextContext{" + "color=" + color + '}';
    }
    
    
}
