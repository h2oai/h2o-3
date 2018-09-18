package water.parser;

import java.util.Arrays;

/**
 * A schema over a string represented as an array of bytes.
 * This schema enables characters to be skipped inside the string. unlike the basic {@link BufferedString}
 * Skipped characters are not serialized by toString method.
 */
class CharSkippingBufferedString extends BufferedString {

    private int[] _skipped;
    private int _skippedWriteIndex;

    public CharSkippingBufferedString() {
        _skipped = new int[0];
        _skippedWriteIndex = 0;
    }

    /**
     * Marks a character in the backing array as skipped. Such character is no longer serialized when toString() method
     * is called on this buffer.
     *
     * @param skippedCharIndex Index of the character in the backing array to skip
     */
    public final void skipIndex(int skippedCharIndex) {
        super.addChar();
        if (_skipped.length == 0 || _skipped[_skipped.length - 1] != -1) {
            _skipped = Arrays.copyOf(_skipped, Math.max(_skipped.length + 1, 1));
        }

        _skipped[_skippedWriteIndex] = skippedCharIndex;
        _skippedWriteIndex++;
    }

    @Override
    public BufferedString set(byte[] buf, int off, int len) {
        _skipped = new int[0];
        _skippedWriteIndex = 0;
        return super.set(buf, off, len);
    }

    @Override
    public String toString() {
        if(getBuffer() == null) return null;
        StringBuilder stringBuilder = new StringBuilder(super.toString());
        int nSkipped = 0;
        for (int skippedChar : _skipped) {
            skippedChar = skippedChar - getOffset() - nSkipped - 1;
            stringBuilder.deleteCharAt(skippedChar);
            nSkipped++;
        }
        return stringBuilder.toString();
    }
}
