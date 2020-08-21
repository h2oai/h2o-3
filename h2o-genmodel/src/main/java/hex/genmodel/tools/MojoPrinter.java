package hex.genmodel.tools;

public interface MojoPrinter {
    enum Format {
        dot, json, raw, png
    }
    void run() throws Exception;
    void parseArgs(String[] args);
    boolean supportsFormat(Format format); 
}
