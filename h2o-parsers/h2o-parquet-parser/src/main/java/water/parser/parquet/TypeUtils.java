package water.parser.parquet;

/**
 * Several helper methods inspired by Guava library - https://github.com/google/guava/. We want to avoid bringing guava dependency when possible.
 *
 * Duplicating some code from that library is a small sacrifice for not bringing the whole dependency
 */
public class TypeUtils {
    /**
     * Returns the {@code long} value whose byte representation is the given 8 bytes, in big-endian
     * order; equivalent to {@code Longs.fromByteArray(new byte[] {b1, b2, b3, b4, b5, b6, b7, b8})}.
     *
     */
    public static long longFromBytes(
            byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        return (b1 & 0xFFL) << 56
                | (b2 & 0xFFL) << 48
                | (b3 & 0xFFL) << 40
                | (b4 & 0xFFL) << 32
                | (b5 & 0xFFL) << 24
                | (b6 & 0xFFL) << 16
                | (b7 & 0xFFL) << 8
                | (b8 & 0xFFL);
    }

    /**
     * Returns the {@code int} value whose byte representation is the given 4 bytes, in big-endian
     * order; equivalent to {@code Ints.fromByteArray(new byte[] {b1, b2, b3, b4})}.
     */
    public static int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }
}
