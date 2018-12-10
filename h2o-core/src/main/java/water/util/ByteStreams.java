package water.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Several helper methods inspired by Guava library. We want to avoid bringing guava dependency when possible.
 * 
 * Duplicating some code from that library is a small sacrifice for not bringing the whole dependency
 */
public class ByteStreams {

    public static void readFully(InputStream in, byte[] b) throws IOException {
        readFully(in, b, 0, b.length);
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int read = read(in, b, off, len);
        if (read != len) {
            throw new EOFException(
                    "reached end of stream after reading " + read + " bytes; " + len + " bytes expected");
        }
    }

    public static int read(InputStream in, byte[] b, int off, int len) throws IOException {
        checkNotNull(in);
        checkNotNull(b);
        if (len < 0) {
            throw new IndexOutOfBoundsException("len is negative");
        }
        int total = 0;
        while (total < len) {
            int result = in.read(b, off + total, len - total);
            if (result == -1) {
                break;
            }
            total += result;
        }
        return total;
    }

    private static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }
}
