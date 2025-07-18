package chiralsoftware.stdout2pdf;

record StringToken(String string) implements Token {
    
@Override
    public boolean visible() { return true; }
@Override
    public int length() { return string.length(); }
    
    StringToken substring(int i) {
        return new StringToken(string.substring(i));
    }
    
}
