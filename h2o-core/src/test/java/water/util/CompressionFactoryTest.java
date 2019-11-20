package water.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.TestBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class CompressionFactoryTest extends TestBase {

  @Rule
  public ExpectedException ee = ExpectedException.none();

  @Test
  public void testGzip() throws IOException {
    try (OutputStream os = CompressionFactory
            .make("gzip").wrapOutputStream(new ByteArrayOutputStream())) {
      assertTrue(os instanceof GZIPOutputStream);
    }
  }

  @Test
  public void testOthers() throws IOException {
    String[] types = new String[]{
            "bzip2", "snappy", DelegatingOutputStream.class.getName()
    };
    for (String type : types) {
      try (OutputStream os = CompressionFactory
              .make(type).wrapOutputStream(new ByteArrayOutputStream())) {
        assertEquals(type, ((DelegatingOutputStream) os).getType());
      }
    }
  }

  @Test
  public void testMissing() throws IOException {
    ee.expectMessage("Cannot create a compressor using class MISSING");
    CompressionFactory.make("MISSING")
            .wrapOutputStream(new ByteArrayOutputStream());
  }

  @Test
  public void testInvalid() throws IOException {
    ee.expectMessage("Cannot create a compressor using class java.util.ArrayList");
    CompressionFactory.make(ArrayList.class.getName())
            .wrapOutputStream(new ByteArrayOutputStream());
  }

  public static class DelegatingOutputStream extends OutputStream {

    private final OutputStream os;

    public DelegatingOutputStream(OutputStream os) {
      this.os = os;
    }

    @Override
    public void write(int b) throws IOException {
      os.write(b);
    }

    @Override
    public void close() throws IOException {
      os.close();
    }

    public String getType() {
      return DelegatingOutputStream.class.getName();
    }
    
  }


}
