package water.api;

import org.junit.Test;
import water.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class NanoResponseTest {

    @Test
    public void testNanoResponseStringConstructorDelegatesToBytesConstructor() throws IOException  {
        NanoResponse nrFromBytes = new NanoResponse("OK", "text/plain", StringUtils.bytesOf("test1"));
        NanoResponse nrFromString = new NanoResponse("OK", "text/plain", "test1");
        try (
                ByteArrayOutputStream expected = new ByteArrayOutputStream();
                ByteArrayOutputStream actual = new ByteArrayOutputStream();
        ) {
            nrFromString.writeTo(expected);
            nrFromBytes.writeTo(actual);
            assertArrayEquals(expected.toByteArray(), actual.toByteArray());
        }
    }

}
