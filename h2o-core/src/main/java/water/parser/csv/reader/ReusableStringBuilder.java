
package water.parser.csv.reader;

import water.util.ArrayUtils;

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

    private byte[] buf;
    private int pos;

    ReusableStringBuilder() {
        buf = new byte[]{};
    }


    public void append(final byte[] src) {
        buf = ArrayUtils.append(buf, src);
        pos += src.length;
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
            buf = new byte[]{};
            pos = 0;
            return s;
        } else {
            buf = new byte[]{};
            return EMPTY;
        }
    }

}
