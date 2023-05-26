package water.util;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;


public class ParseTime {

    /**
     * Factory to create a formatter from a strptime pattern string.
     * This models the commonly supported features of strftime from POSIX
     * (where it can).
     * <p>
     * The format may contain locale specific output, and this will change as
     * you change the locale of the formatter.
     * Call DateTimeFormatter.withLocale(Locale) to switch the locale.
     * For example:
     * <pre>
     * DateTimeFormat.forPattern(pattern).withLocale(Locale.FRANCE).print(dt);
     * </pre>
     *
     * @param pattern  pattern specification
     * @return the formatter
     *  @throws IllegalArgumentException if the pattern is invalid
     */
    public static DateTimeFormatter forStrptimePattern(String pattern) {
        if (pattern == null || pattern.length() == 0)
            throw new IllegalArgumentException("Empty date time pattern specification");

        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        parseToBuilder(builder, pattern);
        DateTimeFormatter formatter = builder.toFormatter();

        return formatter;
    }

    /**
     * Parses the given pattern and appends the rules to the given
     * DateTimeFormatterBuilder. See strptime man page for valid patterns.
     *
     * @param pattern  pattern specification
     * @throws IllegalArgumentException if the pattern is invalid
     */
    private static void parseToBuilder(DateTimeFormatterBuilder builder, String pattern) {
        int length = pattern.length();
        int[] indexRef = new int[1];

        for (int i=0; i<length; i++) {
            indexRef[0] = i;
            String token = parseToken(pattern, indexRef);
            i = indexRef[0];

            int tokenLen = token.length();
            if (tokenLen == 0) {
                break;
            }
            char c = token.charAt(0);

            if (c == '%' && token.charAt(1) != '%') {
                c = token.charAt(1);
                switch(c) {
                    case 'a':
                        builder.appendDayOfWeekShortText();
                        break;
                    case 'A':
                        builder.appendDayOfWeekText();
                        break;
                    case 'b':
                    case 'h':
                        builder.appendMonthOfYearShortText();
                        break;
                    case 'B':
                        builder.appendMonthOfYearText();
                        break;
                    case 'c':
                        builder.appendDayOfWeekShortText();
                        builder.appendLiteral(' ');
                        builder.appendMonthOfYearShortText();
                        builder.appendLiteral(' ');
                        builder.appendDayOfMonth(2);
                        builder.appendLiteral(' ');
                        builder.appendHourOfDay(2);
                        builder.appendLiteral(':');
                        builder.appendMinuteOfHour(2);
                        builder.appendLiteral(':');
                        builder.appendSecondOfMinute(2);
                        builder.appendLiteral(' ');
                        builder.appendYear(4,4);
                        break;
                    case 'C':
                        builder.appendCenturyOfEra(1,2);
                        break;
                    case 'd':
                        builder.appendDayOfMonth(2);
                        break;
                    case 'D':
                        builder.appendMonthOfYear(2);
                        builder.appendLiteral('/');
                        builder.appendDayOfMonth(2);
                        builder.appendLiteral('/');
                        builder.appendTwoDigitYear(2019);
                        break;
                    case 'e':
                        builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
                        builder.appendDayOfMonth(2);
                        break;
                    case 'F':
                        builder.appendYear(4,4);
                        builder.appendLiteral('-');
                        builder.appendMonthOfYear(2);
                        builder.appendLiteral('-');
                        builder.appendDayOfMonth(2);
                        break;
                    case 'g':
                    case 'G':
                        break; //for output only, accepted and ignored for input
                    case 'H':
                        builder.appendHourOfDay(2);
                        break;
                    case 'I':
                        builder.appendClockhourOfHalfday(2);
                        break;
                    case 'j':
                        builder.appendDayOfYear(3);
                        break;
                    case 'k':
                        builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
                        builder.appendHourOfDay(2);
                        break;
                    case 'l':
                        builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
                        builder.appendClockhourOfHalfday(2);
                        break;
                    case 'm':
                        builder.appendMonthOfYear(2);
                        break;
                    case 'M':
                        builder.appendMinuteOfHour(2);
                        break;
                    case 'n':
                        break;
                    case 'p':
                        builder.appendHalfdayOfDayText();
                        break;
                    case 'r':
                        builder.appendClockhourOfHalfday(2);
                        builder.appendLiteral(':');
                        builder.appendMinuteOfHour(2);
                        builder.appendLiteral(':');
                        builder.appendSecondOfMinute(2);
                        builder.appendLiteral(' ');
                        builder.appendHalfdayOfDayText();
                        break;
                    case 'R':
                        builder.appendHourOfDay(2);
                        builder.appendLiteral(':');
                        builder.appendMinuteOfHour(2);
                        break;
                    case 'S':
                        builder.appendSecondOfMinute(2);
                        break;
                    case 't':
                        break;
                    case 'T':
                        builder.appendHourOfDay(2);
                        builder.appendLiteral(':');
                        builder.appendMinuteOfHour(2);
                        builder.appendLiteral(':');
                        builder.appendSecondOfMinute(2);
                        break;
/*          case 'U':  //FIXME Joda does not support US week start (Sun), this will be wrong
            builder.appendWeekOfYear(2);
            break;
          case 'u':
            builder.appendDayOfWeek(1);
            break;*/
                    case 'V':
                        break; //accepted and ignored
/*          case 'w':  //FIXME Joda does not support US week start (Sun), this will be wrong
            builder.appendDayOfWeek(1);
            break;
          case 'W':
            builder.appendWeekOfYear(2);
            break;*/
                    case 'x':
                        builder.appendTwoDigitYear(2019);
                        builder.appendLiteral('/');
                        builder.appendMonthOfYear(2);
                        builder.appendLiteral('/');
                        builder.appendDayOfMonth(2);
                        break;
/*          case 'X':  //Results differ between OSX and Linux
            builder.appendHourOfDay(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            builder.appendLiteral(':');
            builder.appendSecondOfMinute(2);
            break;*/
                    case 'y': //POSIX 2004 & 2008 says 69-99 -> 1900s, 00-68 -> 2000s
                        builder.appendTwoDigitYear(2019);
                        break;
                    case 'Y':
                        builder.appendYear(4,4);
                        break;
                    case 'z':
                        builder.appendTimeZoneOffset(null, "z", false, 2, 2);
                        break;
                    case 'Z':
                        break;  //for output only, accepted and ignored for input
                    default:  // No match, ignore
                        builder.appendLiteral('\'');
                        builder.appendLiteral(token);
                        throw new IllegalArgumentException(token + "is not acceptted as a parse token, treating as a literal");
                }
            } else {
                if (c == '\'') {
                    String sub = token.substring(1);
                    if (sub.length() > 0) {
                        // Create copy of sub since otherwise the temporary quoted
                        // string would still be referenced internally.
                        builder.appendLiteral(new String(sub));
                    }
                } else throw new IllegalArgumentException("Unexpected token encountered parsing format string:" + c);
            }
        }
    }

    /**
     * Parses an individual token.
     *
     * @param pattern  the pattern string
     * @param indexRef  a single element array, where the input is the start
     *  location and the output is the location after parsing the token
     * @return the parsed token
     */
    private static String parseToken(String pattern, int[] indexRef) {
        StringBuilder buf = new StringBuilder();

        int i = indexRef[0];
        int length = pattern.length();

        char c = pattern.charAt(i);
        if (c == '%' && i + 1 < length && pattern.charAt(i+1) != '%') {
            //Grab pattern tokens
            c = pattern.charAt(++i);
            //0 is ignored for input, and this ignores alternative religious eras
            if ((c == '0' || c == 'E') && i + 1 >= length) c = pattern.charAt(++i);
            buf.append('%');
            buf.append(c);
        } else { // Grab all else as text
            buf.append('\'');  // mark literals with ' in first place
            buf.append(c);
            for (i++; i < length;i++) {
                c = pattern.charAt(i);
                if (c == '%' ) { // consume literal % otherwise break
                    if (i + 1 < length && pattern.charAt(i + 1) == '%') i++;
                    else { i--; break; }
                }
                buf.append(c);
            }
        }

        indexRef[0] = i;
        return buf.toString();
    }
}
