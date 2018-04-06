
package water.parser.csv.reader;

import java.util.Arrays;

/**
 * Resettable / reusable and thus high performance replacement for StringBuilder.
 *
 * This class is intended for internal use only.
 *
 * @author Oliver Siegmar
 */

final class ReusableStringBuilder {

    private static final String EMPTY = "";

    private char[] buf;
    private int pos;

    /**
     * Initializes the buffer with the specified capacity.
     *
     * @param initialCapacity the initial buffer capacity.
     */
    ReusableStringBuilder(final int initialCapacity) {
        buf = new char[initialCapacity];
    }

    /**
     * Appends a character to the buffer, resizing the buffer if needed.
     *
     * @param c the character to add to the buffer
     */
    public void append(final char c) {
        if (pos == buf.length) {
            buf = Arrays.copyOf(buf, buf.length * 2);
        }
        buf[pos++] = c;
    }

    public void append(final char[] src, final int srcPos, final int length) {
        if (pos + length > buf.length) {
            int newSize = buf.length * 2;
            while (pos + length > newSize) {
                newSize *= 2;
            }
            buf = Arrays.copyOf(buf, newSize);
        }
        System.arraycopy(src, srcPos, buf, pos, length);
        pos += length;
    }

    /**
     * @return {@code true} if the buffer contains content
     */
    public boolean hasContent() {
        return pos > 0;
    }

    /**
     * Returns the string representation of the buffer and resets the buffer.
     *
     * @return the string representation of the buffer
     */
    public String toStringAndReset() {
        if (pos > 0) {
            final String s = new String(buf, 0, pos);
            pos = 0;
            return s;
        }
        return EMPTY;
    }

}
