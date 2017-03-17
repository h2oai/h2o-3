package hex.genmodel;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class InMemoryMojoReaderBackendTest {

  private InMemoryMojoReaderBackend readerBackend;

  @Before
  public void setup() {
    Map<String, byte[]> content = new HashMap<>();
    content.put("text-file", "line1\nline2\n".getBytes());
    content.put("binary-file", new byte[]{0, 1, 2});
    readerBackend = new InMemoryMojoReaderBackend(content);
  }

  @Test
  public void testGetTextFile() throws Exception {
    try (BufferedReader r = readerBackend.getTextFile("text-file")) {
      assertEquals("line1", r.readLine());
      assertEquals("line2", r.readLine());
      assertNull(r.readLine());
    }
  }

  @Test
  public void testGetBinaryFile() throws Exception {
    byte[] data = readerBackend.getBinaryFile("binary-file");
    assertArrayEquals(new byte[]{0, 1, 2}, data);
  }

  @Test
  public void testExists() throws Exception {
    assertFalse(readerBackend.exists("invalid-file"));
    assertTrue(readerBackend.exists("text-file"));
  }

  @Test
  public void testClose() throws Exception {
    readerBackend.close();
    try {
      readerBackend.exists(null);
      fail("Exception expected");
    } catch (IllegalStateException e) {
      assertEquals("ReaderBackend was already closed", e.getMessage());
    }
  }

}