import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.nio.channels.SeekableByteChannel;
import java.util.Scanner;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This class currently assumes that all java source files only contain one class in them
 * TODO: Add support for parsing more than one class in each source file
 **/
public class ClassParser {
    private final static Pattern _PACKAGE_HEADER = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;", Pattern.MULTILINE);
    private final static Pattern _CLASS_HEADER = Pattern.compile("class\\s+(\\w+)[^{]*\\{", Pattern.MULTILINE);
    private final static Pattern _LEFT_BRACE = Pattern.compile("\\{");
    private final static Pattern _RIGHT_BRACE = Pattern.compile("}");
    private final static Pattern _DOUBLE_QUOTES = Pattern.compile("\"(?:\\.|[^\"])*\"");
    private final static Pattern _SINGLE_QUOTES = Pattern.compile("\'(?:\\.|[^\'])?\'");
    private final static Pattern _LINE_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    private final static Pattern _BLOCK_COMMENT = Pattern.compile("/\\*(?:[^*]|\\*[^/])*(?:\\*/)?", Pattern.MULTILINE);
    private enum Mode {
        NONE, CLASS_BLOCK, BLOCK, DOUBLE_QUOTE, SINGLE_QUOTE, BLOCK_COMMENT, LINE_COMMENT
    }

    public static void parse(Path p) throws IOException {
        String enc = System.getProperty("file.encoding");
        SeekableByteChannel sbc = Files.newByteChannel(p);
        ByteBuffer bb = ByteBuffer.allocate(30);
        while (sbc.read(bb) > -1) {
            bb.rewind();
            System.out.println(enc);
            System.out.println(Charset.forName(enc).decode(bb));
            Matcher m = _LEFT_BRACE.matcher(Charset.forName(enc).decode(bb));
            System.out.println(m.find());
            if (m.find()) {
                System.out.println("YES!");
            }
            bb.flip();
        }
    }

    public static void main (String[] args) {
        try {
            ClassParser.parse(Paths.get("/Users/nkalonia1/h2o-3/jacoco/coverage_tool/test_files/parse_test.txt"));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}