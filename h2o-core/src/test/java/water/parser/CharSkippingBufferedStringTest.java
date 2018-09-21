package water.parser;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CharSkippingBufferedStringTest {


    private CharSkippingBufferedString charSkippingBufferedString;

    @Before
    public void setUp() {
        charSkippingBufferedString = new CharSkippingBufferedString();
    }

    @Test
    public void toBufferedString() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,0, bytes.length - 1);
        charSkippingBufferedString.skipIndex(bytes.length - 1);

        assertNotNull(charSkippingBufferedString.getBuffer());

        final BufferedString bufferedString = charSkippingBufferedString.toBufferedString();
        assertNotNull(bufferedString.getBuffer());
        assertEquals(3, bufferedString.length());
        assertEquals(0, bufferedString.getOffset());

        assertEquals("abc", bufferedString.toString());
    }

    @Test
    public void toBufferedString_nonZeroOffset() {
        final byte[] bytes = "abcdefgh".getBytes();
        charSkippingBufferedString.set(bytes,4, 0);
        charSkippingBufferedString.skipIndex(4);
        charSkippingBufferedString.addChar();
        charSkippingBufferedString.addChar();
        charSkippingBufferedString.addChar();

        assertNotNull(charSkippingBufferedString.getBuffer());

        final BufferedString bufferedString = charSkippingBufferedString.toBufferedString();
        assertNotNull(bufferedString.getBuffer());
        assertEquals(3, bufferedString.length());
        assertEquals(0, bufferedString.getOffset());

        assertEquals("fgh", bufferedString.toString());
    }

    @Test
    public void toBufferedString_skipFirst() {
        final byte[] bytes = "efgh".getBytes();
        charSkippingBufferedString.set(bytes,0, 0);
        charSkippingBufferedString.skipIndex(0);
        charSkippingBufferedString.addChar();
        charSkippingBufferedString.addChar();
        charSkippingBufferedString.addChar();

        assertNotNull(charSkippingBufferedString.getBuffer());

        final BufferedString bufferedString = charSkippingBufferedString.toBufferedString();
        assertNotNull(bufferedString.getBuffer());
        assertEquals(3, bufferedString.length());
        assertEquals(0, bufferedString.getOffset());

        assertEquals("fgh", bufferedString.toString());
    }

    @Test
    public void removeChar() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,0, bytes.length);

        charSkippingBufferedString.removeChar();
        assertEquals("abc", charSkippingBufferedString.toString());
    }

    @Test
    public void addChar() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,0, 0);
        charSkippingBufferedString.addChar();

        assertEquals("a", charSkippingBufferedString.toString());
    }

    @Test
    public void emptyStringBehavior() {
        final byte[] bytes = "".getBytes();
        charSkippingBufferedString.set(bytes,0, 0);
        final String string = charSkippingBufferedString.toString();
        assertNotNull(string);
        assertTrue(string.isEmpty());
    }

    @Test
    public void testToString() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,0, 0);

        assertEquals(charSkippingBufferedString.toString(), charSkippingBufferedString.toBufferedString().toString());
    }
}