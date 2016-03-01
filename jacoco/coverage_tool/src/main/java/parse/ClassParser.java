package parse;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A parser of *.java files. Returns a list of ClassHunks
 * A ClassHunk is an object that contains the name of a class (package included) and the line number range it occupies
 **/
public class ClassParser {
    private final static Pattern _PACKAGE_HEADER = Pattern.compile("package");
    private final static Pattern _CLASS_HEADER = Pattern.compile("class");
    private final static Pattern _NAME = Pattern.compile("([a-zA-Z_][a-zA-Z0-9._]*)");
    private final static Pattern _LEFT_BRACE = Pattern.compile("\\{");
    private final static Pattern _RIGHT_BRACE = Pattern.compile("}");
    private final static Pattern _DOUBLE_QUOTE = Pattern.compile("\"");
    private final static Pattern _SINGLE_QUOTE = Pattern.compile("'");
    private final static Pattern _LINE_COMMENT = Pattern.compile("//");
    private final static Pattern _ESCAPE = Pattern.compile("\\\\");
    private final static Pattern _WHITESPACE = Pattern.compile("\\s");
    private final static Pattern _NEWLINE = Pattern.compile("\\n");
    private final static Pattern _BLOCK_COMMENT_OPEN = Pattern.compile("/\\*");
    private final static Pattern _BLOCK_COMMENT_CLOSE = Pattern.compile("\\*/");

    private enum Type {
        NONE, BLOCK, CLASS_BLOCK, CLASS_HEADER, CLASS_NAME, CLASS_BLOCK_START, PACKAGE_HEADER, PACKAGE_NAME,  LINE_COMMENT, BLOCK_COMMENT, SINGLE_QUOTE, DOUBLE_QUOTE
    }

    public static List<ClassHunk> parse(Path p) throws IOException {
        return parse(p, System.getProperty("file.encoding"));
    }

    public static List<ClassHunk> parse (Path p, String encoding) throws IOException {
        FileChannel fc = FileChannel.open(p);
        Stack<Tuple> s = new Stack<Tuple>();
        Stack<String> className = new Stack<String>();
        List<ClassHunk> classList = new ArrayList<ClassHunk>();
        s.push(new Tuple(Type.NONE));
        int line_number = 1;
        int size = (int) fc.size();
        ByteBuffer bb = ByteBuffer.allocate(size);
        while (bb.hasRemaining()) {
            fc.read(bb);
        }
        bb.flip();
        CharBuffer cb = Charset.forName(encoding).decode(bb);
        Matcher m = Pattern.compile("").matcher(cb);
        while (cb.hasRemaining()) {
            switch (s.peek().getType()) {
                case LINE_COMMENT:
                    //System.out.println("LINE COMMENT MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        s.pop();
                        break;
                    }
                    cb.get();
                    break;

                case BLOCK_COMMENT:
                    //System.out.println("BLOCK COMMENT MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_CLOSE))) {
                        //System.out.println("FOUND BLOCK COMMENT END");
                        cb.position(cb.position() + m.end());
                        s.pop();
                        break;
                    }
                    cb.get();
                    break;

                case SINGLE_QUOTE:
                    //System.out.println("SINGLE QUOTE MODE");
                    if (lookingAt(m.usePattern(_SINGLE_QUOTE))) {
                        //System.out.println("FOUND SINGLE QUOTE");
                        cb.position(cb.position() + m.end());
                        s.pop();
                        break;
                    }
                    if (lookingAt(m.usePattern(_ESCAPE))) {
                        //System.out.println("FOUND ESCAPE CHARACTER");
                        cb.position(cb.position() + m.end());
                        cb.get();
                        break;
                    }
                    cb.get();
                    break;

                case DOUBLE_QUOTE:
                    //System.out.println("DOUBLE QUOTE MODE");
                    if (lookingAt(m.usePattern(_DOUBLE_QUOTE))) {
                        //System.out.println("FOUND DOUBLE QUOTE");
                        cb.position(cb.position() + m.end());
                        s.pop();
                        break;
                    }
                    if (lookingAt(m.usePattern(_ESCAPE))) {
                        //System.out.println("FOUND ESCAPE CHARACTER");
                        cb.position(cb.position() + m.end());
                        cb.get();
                        break;
                    }
                    cb.get();
                    break;
                case PACKAGE_NAME:
                    if (lookingAt(m.usePattern(_NAME))) {
                        //System.out.println("FOUND NAME:" + m.group());
                        className.push(m.group());
                        s.pop();
                        cb.position(cb.position() + m.end());
                        break;
                    }
                case PACKAGE_HEADER:
                    //System.out.println("PACKAGE HEADER MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        s.pop();
                        s.push(new Tuple(Type.PACKAGE_NAME));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_WHITESPACE))) {
                        //System.out.println("FOUND WHITESPACE");
                        s.pop();
                        s.push(new Tuple(Type.PACKAGE_NAME));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    //System.out.println("PACKAGE NAME NOT FOUND");
                    s.pop();
                    break;
                case CLASS_BLOCK_START:
                    //System.out.println("CLASS BLOCK START MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_WHITESPACE))) {
                        //System.out.println("FOUND WHITESPACE");
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LEFT_BRACE))) {
                        //System.out.println("FOUND CLASS START");
                        s.pop();
                        String n = "";
                        Iterator<String> i = className.iterator();
                        if (i.hasNext()) n += i.next();
                        while (i.hasNext()) {
                            n += "." + i.next();
                        }
                        s.push(new Tuple(Type.CLASS_BLOCK, new ClassHunkBuilder(n, line_number)));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    cb.get();
                    break;
                case CLASS_NAME:
                    if (lookingAt(m.usePattern(_NAME))) {
                        //System.out.println("FOUND NAME:" + m.group());
                        className.push(m.group());
                        s.pop();
                        s.push(new Tuple(Type.CLASS_BLOCK_START));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                case CLASS_HEADER:
                    //System.out.println("CLASS HEADER MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        s.pop();
                        s.push(new Tuple(Type.CLASS_NAME));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_WHITESPACE))) {
                        //System.out.println("FOUND WHITESPACE");
                        s.pop();
                        s.push(new Tuple(Type.CLASS_NAME));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    //System.out.println("CLASS NAME NOT FOUND");
                    s.pop();
                    break;
                case BLOCK:
                    //System.out.println("BLOCK MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LEFT_BRACE))) {
                        //System.out.println("FOUND BLOCK");
                        s.push(new Tuple(Type.BLOCK));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_SINGLE_QUOTE))) {
                        //System.out.println("FOUND SINGLE QUOTE");
                        s.push(new Tuple(Type.SINGLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_DOUBLE_QUOTE))) {
                        //System.out.println("FOUND DOUBLE QUOTES");
                        s.push(new Tuple(Type.DOUBLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_CLASS_HEADER))) {
                        //System.out.println("FOUND CLASS HEADER");
                        s.push(new Tuple(Type.CLASS_HEADER));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_RIGHT_BRACE))) {
                        //System.out.println("FOUND BLOCK END");
                        s.pop();
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    cb.get();
                    break;
                case CLASS_BLOCK:
                    //System.out.println("CLASS BLOCK MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LEFT_BRACE))) {
                        //System.out.println("FOUND BLOCK");
                        s.push(new Tuple(Type.BLOCK));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_SINGLE_QUOTE))) {
                        //System.out.println("FOUND SINGLE QUOTE");
                        s.push(new Tuple(Type.SINGLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_DOUBLE_QUOTE))) {
                        //System.out.println("FOUND DOUBLE QUOTES");
                        s.push(new Tuple(Type.DOUBLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_CLASS_HEADER))) {
                        //System.out.println("FOUND CLASS HEADER");
                        s.push(new Tuple(Type.CLASS_HEADER));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_RIGHT_BRACE))) {
                        //System.out.println("FOUND CLASS BLOCK END");
                        className.pop();
                        classList.add(s.pop().getBuilder().end(line_number));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    cb.get();
                    break;
                default:
                    //System.out.println("MAIN MODE");
                    if (lookingAt(m.usePattern(_NEWLINE))) {
                        //System.out.println("FOUND NEWLINE");
                        line_number += 1;
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LEFT_BRACE))) {
                        //System.out.println("FOUND BLOCK");
                        s.push(new Tuple(Type.BLOCK));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_LINE_COMMENT))) {
                        //System.out.println("FOUND LINE COMMENT");
                        s.push(new Tuple(Type.LINE_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_BLOCK_COMMENT_OPEN))) {
                        //System.out.println("FOUND BLOCK COMMENT");
                        s.push(new Tuple(Type.BLOCK_COMMENT));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_SINGLE_QUOTE))) {
                        //System.out.println("FOUND SINGLE QUOTE");
                        s.push(new Tuple(Type.SINGLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_DOUBLE_QUOTE))) {
                        //System.out.println("FOUND DOUBLE QUOTES");
                        s.push(new Tuple(Type.DOUBLE_QUOTE));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_CLASS_HEADER))) {
                        //System.out.println("FOUND CLASS HEADER");
                        s.push(new Tuple(Type.CLASS_HEADER));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    if (lookingAt(m.usePattern(_PACKAGE_HEADER))) {
                        //System.out.println("FOUND PACKAGE HEADER");
                        s.push(new Tuple(Type.PACKAGE_HEADER));
                        cb.position(cb.position() + m.end());
                        break;
                    }
                    cb.get();
                    break;
            }
        }
        if (s.peek().getType() != Type.NONE) throw new IllegalStateException("EOF reached mid-parse");
        return classList;
    }

    private static boolean lookingAt(Matcher m) {
        try {
            return m.lookingAt();
        } catch (IndexOutOfBoundsException ioobe) {
            return false;
        } catch (BufferUnderflowException bue) {
            return false;
        }
    }

    static class Tuple {
        private Type _t;
        private ClassHunkBuilder _c;

        public Tuple(Type t) { _t=t; _c=new InvalidClassHunkBuilder(); }
        public Tuple(Type t, ClassHunkBuilder chb) { _t=t; _c=chb; }

        public Type getType() { return _t; }
        public ClassHunkBuilder getBuilder() { return _c; }
    }

    public static void main (String[] args) {
        try {
            List<ClassHunk> l = ClassParser.parse(Paths.get("/Users/nkalonia1/h2o-3/jacoco/coverage_tool/test_files/GLM.java"));
            for (ClassHunk ch : l) {
                System.out.println(ch);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}