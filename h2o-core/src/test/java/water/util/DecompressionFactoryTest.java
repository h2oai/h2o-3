package water.util;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class DecompressionFactoryTest {

    @Rule
    public ExpectedException ee = ExpectedException.none();
    
    @Test
    public void testGzip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream os = new GZIPOutputStream(baos)) {
            os.write("42".getBytes());
        }
        try (InputStream is = DecompressionFactory
                .make("gzip").wrapInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            assertTrue(is instanceof GZIPInputStream);
        }
    }

    @Test
    public void testOthers() throws IOException {
        String type = DelegatingInputStream.class.getName();
        try (InputStream is = DecompressionFactory
                .make(type).wrapInputStream(new ByteArrayInputStream("42".getBytes()))) { 
            assertEquals(type, ((DelegatingInputStream) is).getType());
        }
    }

    @Test
    public void testMissing() throws IOException {
        ee.expectMessage("Cannot create a decompressor using class MISSING");
        DecompressionFactory.make("MISSING")
                .wrapInputStream(new ByteArrayInputStream("42".getBytes()));
    }

    @Test
    public void testInvalid() throws IOException {
        ee.expectMessage("Cannot create a decompressor using class java.util.ArrayList");
        DecompressionFactory.make(ArrayList.class.getName())
                .wrapInputStream(new ByteArrayInputStream("42".getBytes()));
    }

    public static class DelegatingInputStream extends InputStream {

        private final InputStream is;

        public DelegatingInputStream(InputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public void close() throws IOException {
            is.close();
        }

        public String getType() {
            return DelegatingInputStream.class.getName();
        }

    }


}
