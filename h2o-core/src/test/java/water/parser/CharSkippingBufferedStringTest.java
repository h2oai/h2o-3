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
        assertEquals(4, charSkippingBufferedString.length());
        assertEquals(0, charSkippingBufferedString.getOffset());

        final BufferedString bufferedString = charSkippingBufferedString.toBufferedString();
        assertNotNull(bufferedString.getBuffer());
        assertEquals(3, bufferedString.length());
        assertEquals(0, bufferedString.getOffset());

        assertEquals("abc", bufferedString.toString());
    }

    @Test
    public void removeChar() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,0, bytes.length);
        assertEquals(4, charSkippingBufferedString.length());

        charSkippingBufferedString.removeChar();
        assertEquals(3, charSkippingBufferedString.length());
    }

    @Test
    public void getOffset() {
        final byte[] bytes = "abcd".getBytes();
        charSkippingBufferedString.set(bytes,1, bytes.length);
        assertEquals(1, charSkippingBufferedString.getOffset());
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