package water.rapids;

import water.rapids.ast.AstExec;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.*;
import water.util.CollectionUtils;
import water.util.StringUtils;

import java.util.*;

/**
 * This file contains the AstRoot parser and parser helper functions.
 */
public class RapidsParser {
    private final String _str;  // Statement to parse and execute
    private int _x;             // Parse pointer, points to the index of the next character to be consumed
    private Deque<AstRoot> out = new LinkedList<>();
    private Deque<Integer> counter = new LinkedList<>();

    /**
     * Parse and return the expression represented by the rapids string.
     * '('   a nested function application expression ')
     * '{'   a nested function definition  expression '}'
     * '['   a numeric list expression, till ']'
     * '"'   a String (double quote): attached_token
     * "'"   a String (single quote): attached_token
     * digits: a double
     * letters or other specials: an ID
     */
    private AstRoot parse(){
        while(_x < _str.length()){
            switch(firstNonWhiteSpaceChar()){
                case '(':
                    parseFunctionApplicationOpen();
                    break;
                case ')':
                    parseFunctionApplicationClose();
                    break;
                case '{':
                    parseFunctionDefinitionOpen();
                    break;
                case '}':
                    parseFunctionDefinitionClose();
                    break;
                case '[':
                    addRapidsOutput(parseList());
                    break;
                case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    addRapidsOutput(new AstNum(parseNumber()));
                    break;
                case '-':
                    if(peek(1)>='0' && peek(1) <='9') {
                        addRapidsOutput(new AstNum(parseNumber()));
                    } else {
                        addRapidsOutput(new AstId(parseToken()));
                    }
                    break;
                case '\"': case '\'':
                    addRapidsOutput(new AstStr(parseString()));
                    break;
                case ' ':
                    throw new Rapids.IllegalASTException("Expected an expression but ran out of text");
                default:
                    addRapidsOutput(new AstId(parseToken()));
            }
        }

        AstRoot res = out.pollLast(); // if the Rapids expression is correct, there should be only single element
        if (out.size() != 0){
            throw new Rapids.IllegalASTException("Syntax error: illegal Rapids expression `" + _str + "`");
        }
        return res;
    }
    
    public static AstRoot parse(String rapids) {
        return new RapidsParser(rapids).parse();
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Private
    //--------------------------------------------------------------------------------------------------------------------

    private void addRapidsOutput(AstRoot rapids){
        out.add(rapids);
        if(!counter.isEmpty()) {
            counter.add(counter.pollLast() + 1);
        }
    }

    /**
     * Parse "function application" expression, i.e. pattern of the form "(func ...args)"
     *
     * This method handles the opening of the function application expression
     */
    private void parseFunctionApplicationOpen(){
        eatChar('(');
        counter.add(0); // create a new counter
    }

    /**
     * Parse "function application" expression, i.e. pattern of the form "(func ...args)"
     *
     * This method actually creates AstExec for the function application
     */
    private void parseFunctionApplicationClose(){
        eatChar(')');

        ArrayList<AstRoot> asts = new ArrayList<>();

        int numPoll = counter.pollLast();
        for(int i = 0; i<numPoll; i++){
            asts.add(out.pollLast());
        }
        Collections.reverse(asts);

        AstExec res = new AstExec(asts);
        if (peek(0) == '-') {
            eatChar('-');
            eatChar('>');
            AstId tmpid = new AstId(parseToken());
            res = new AstExec(new AstRoot[]{new AstId("tmp="), tmpid, res});
        }
        addRapidsOutput(res);
    }

    /**
     * Parse and return a user defined function of the form "{arg1 arg2 . (expr)}"
     *
     * This method handles the opening of the function definition expression
     */
    private void parseFunctionDefinitionOpen(){
        eatChar('{');
        counter.add(0); // create a new counter

        // Parse the list of ids
        addRapidsOutput(new AstStr("")); // 1-based ID list
        while (firstNonWhiteSpaceChar() != '.') {
            String id = parseToken();
            if (!Character.isJavaIdentifierStart(id.charAt(0)))
                throw new Rapids.IllegalASTException("variable must be a valid Java identifier: " + id);
            for (char c : id.toCharArray())
                if (!Character.isJavaIdentifierPart(c))
                    throw new Rapids.IllegalASTException("variable must be a valid Java identifier: " + id);
            addRapidsOutput(new AstStr(id));
        }

        // Single dot separates the list of ids from the body of the function
        eatChar('.');
    }

    /**
     * Parse and return a user defined function of the form "{arg1 arg2 . (expr)}"
     *
     * This method actually creates Rapids expression for the function definition
     */
    private void parseFunctionDefinitionClose(){
        // Parse the body
        eatChar('}');

        AstRoot body = out.pollLast();

        ArrayList<String> argNames = new ArrayList<>();
        int numPoll = counter.pollLast() - 1; // -1 because of the body obtained above
        for(int i = 0; i < numPoll; i++){
            argNames.add(out.pollLast().exec(null).getStr()); // get back the string value of argument name
        }
        Collections.reverse(argNames);

        addRapidsOutput(new AstFunction(argNames, body));
    }

    // Set of characters that cannot appear inside a token
    private static Set<Character> invalidTokenCharacters = StringUtils.toCharacterSet("({[]}) \t\r\n\\\"\'");

    // Set of characters that may appear in a number. Note that "NaN" or "nan" is also a number.
    private static Set<Character> validNumberCharacters = StringUtils.toCharacterSet("0123456789.-+eEnNaA");

    // List of all "simple" backslash-escape sequences (i.e. those that are only 2-characters long, i.e. '\n')
    private static Map<Character, Character> simpleEscapeSequences =
            CollectionUtils.createMap(StringUtils.toCharacterArray("ntrfb'\"\\"),
                    StringUtils.toCharacterArray("\n\t\r\f\b'\"\\"));


    /**
     * The constructor is private: rapids expression can be parsed into an AST tree, or executed, but the "naked" Rapids
     * object has no external purpose.
     * @param rapidsStr String containing a Rapids expression.
     */
    private RapidsParser(String rapidsStr) {
        _str = rapidsStr.trim();
        _x = 0;
    }


    /**
     * Parse and return a list of tokens: either a list of strings, or a list of numbers.
     * We do not support lists of mixed types, or lists containing variables (for now).
     */
    private AstParameter parseList() {
        eatChar('[');
        char nextChar = firstNonWhiteSpaceChar();
        AstParameter res = isQuote(nextChar)? parseStringList() : parseNumList();
        eatChar(']');
        return res;
    }

    /**
     * Parse a list of strings. Strings can be either in single- or in double quotes.
     */
    private AstStrList parseStringList() {
        ArrayList<String> strs = new ArrayList<>(10);
        while (isQuote(firstNonWhiteSpaceChar())) {
            strs.add(parseString());
            if (firstNonWhiteSpaceChar() == ',') eatChar(',');
        }
        return new AstStrList(strs);
    }

    /**
     * Parse a "num list". This could be either a plain list of numbers, or a range, or a list of ranges. For example
     * [2 3 4 5 6 7] can also be written as [2:6] or [2:2 4:4:1]. The format of each "range" is `start:count[:stride]`,
     * and it denotes the sequence {start, start + stride, ..., start + (count-1)*stride}. Here start and stride may
     * be real numbers, however count must be a non-negative integer. Negative strides are also not allowed.
     */
    private AstNumList parseNumList() {
        ArrayList<Double> bases = new ArrayList<>();
        ArrayList<Double> strides = new ArrayList<>();
        ArrayList<Long> counts = new ArrayList<>();

        while (firstNonWhiteSpaceChar() != ']') {
            double base = parseNumber();
            double count = 1;
            double stride = 1;
            if (firstNonWhiteSpaceChar() == ':') {
                eatChar(':');
                firstNonWhiteSpaceChar();
                count = parseNumber();
                if (count < 1 || ((long) count) != count)
                    throw new Rapids.IllegalASTException("Count must be a positive integer, got " + count);
            }
            if (firstNonWhiteSpaceChar() == ':') {
                eatChar(':');
                firstNonWhiteSpaceChar();
                stride = parseNumber();
                if (stride < 0 || Double.isNaN(stride))
                    throw new Rapids.IllegalASTException("Stride must be positive, got " + stride);
            }
            if (count == 1 && stride != 1)
                throw new Rapids.IllegalASTException("If count is 1, then stride must be one (and ignored)");
            bases.add(base);
            counts.add((long) count);
            strides.add(stride);
            // Optional comma separating span
            if (firstNonWhiteSpaceChar() == ',') eatChar(',');
        }

        return new AstNumList(bases, strides, counts);
    }

    /**
     * Return the character at the current parse position (or `offset` chars in the future), without advancing it.
     * If there are no more characters to peek, return ' '.
     */
    private char peek(int offset) {
        return _x + offset < _str.length() ? _str.charAt(_x + offset) : ' ';
    }

    /**
     * Consume the next character from the parse stream, throwing an exception if it is not `c`.
     */
    private void eatChar(char c) {
        if (peek(0) != c)
            throw new Rapids.IllegalASTException("Expected '" + c + "'. Got: '" + peek(0));
        _x++;
    }

    /**
     * Advance parse pointer to the first non-whitespace character, and return that character.
     * If such non-whitespace character cannot be found, then return ' '.
     */
    private char firstNonWhiteSpaceChar() {
        char c = ' ';
        while (_x < _str.length() && isWS(c = peek(0))) _x++;
        return c;
    }

    /**
     * Parse a "token" from the input stream. A token is terminated by the next whitespace, or any of the
     * following characters: )}],:
     *
     * NOTE: our notion of "token" is very permissive. We may want to restrict it in the future...
     */
    private String parseToken() {
        int start = _x;
        while (!invalidTokenCharacters.contains(peek(0))) _x++;
        if (start == _x) throw new Rapids.IllegalASTException("Missing token");
        return _str.substring(start, _x);
    }

    /**
     * Parse a number from the token stream.
     */
    private double parseNumber() {
        int start = _x;
        while (validNumberCharacters.contains(peek(0))) _x++;
        if (start == _x) throw new Rapids.IllegalASTException("Missing a number");
        String s = _str.substring(start, _x);
        if (s.toLowerCase().equals("nan")) return Double.NaN;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw new Rapids.IllegalASTException(e.toString());
        }
    }

    /**
     * Parse a string from the token stream.
     */
    private String parseString() {
        char quote = peek(0);
        int start = ++_x;
        boolean has_escapes = false;
        while (_x < _str.length()) {
            char c = peek(0);
            if (c == '\\') {
                has_escapes = true;
                char cc = peek(1);
                if (simpleEscapeSequences.containsKey(cc)) {
                    _x += 2;
                } else if (cc == 'x') {
                    _x += 4;   // e.g: \x5A
                } else if (cc == 'u') {
                    _x += 6;   // e.g: \u1234
                } else if (cc == 'U') {
                    _x += 10;  // e.g: \U0010FFFF
                } else
                    throw new Rapids.IllegalASTException("Invalid escape sequence \\" + cc);
            } else if (c == quote) {
                _x++;
                if (has_escapes) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < _x - 1; i++) {
                        char ch = _str.charAt(i);
                        if (ch == '\\') {
                            char cc = _str.charAt(++i);
                            if (simpleEscapeSequences.containsKey(cc)) {
                                sb.append(simpleEscapeSequences.get(cc));
                            } else {
                                int n = (cc == 'x')? 2 : (cc == 'u')? 4 : (cc == 'U')? 8 : -1;
                                int hex;
                                try {
                                    hex = StringUtils.unhex(_str.substring(i + 1, i + 1 + n));
                                } catch (NumberFormatException e) {
                                    throw new Rapids.IllegalASTException(e.toString());
                                }
                                if (hex > 0x10FFFF)
                                    throw new Rapids.IllegalASTException("Illegal unicode codepoint " + hex);
                                sb.append(Character.toChars(hex));
                                i += n;
                            }
                        } else {
                            sb.append(ch);
                        }
                    }
                    return sb.toString();
                } else {
                    return _str.substring(start, _x - 1);
                }
            } else {
                _x++;
            }
        }
        throw new Rapids.IllegalASTException("Unterminated string at " + start);
    }

    /**
     * Return true if `c` is a whitespace character.
     */
    private static boolean isWS(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /**
     * Return true if `c` is a quote character.
     */
    private static boolean isQuote(char c) {
        return c == '\'' || c == '\"';
    }
}
