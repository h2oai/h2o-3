package water.util;

import org.junit.Test;
import water.TestBase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

public class LogArchiveWriterTest extends TestBase {

  private final Date date1 = new Date(10);
  private final Date date2 = new Date(20);

  @Test
  public void testConcatenatedLogArchiveWriter() throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         LogArchiveWriter writer = new ConcatenatedLogArchiveWriter(baos)) {

      writeEntries(writer);

      String output = new String(baos.toByteArray());
      String expected = 
              "\n" +
              "file1 (" + date1.toString() + "):\n" +
              "=====================================\n" +
              "content1\n" +
              "file2 (" + date2.toString() + "):\n" +
              "=====================================\n" +
              "content2";

      assertEquals(expected, output);
    }
  }

  @Test
  public void testZipLogArchiveWriter() throws IOException {
    byte[] bytes;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         LogArchiveWriter writer = new ZipLogArchiveWriter(baos)) {

      writeEntries(writer);

      bytes = baos.toByteArray();
    }

    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
         ZipInputStream zis = new ZipInputStream(bais)) {
      ZipEntry entry1 = zis.getNextEntry();
      assertEquals("file1", entry1.getName());
      assertEquals("content1", new String(readContent(zis)));
      ZipEntry entry2 = zis.getNextEntry();
      assertEquals("file2", entry2.getName());
      assertEquals("content2", new String(readContent(zis)));
    }
  }

  private static byte[] readContent(ZipInputStream zis) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    int len;
    byte[] buffer = new byte[521];
    while ((len = zis.read(buffer)) > 0) {
      bos.write(buffer, 0, len);
    }
    return bos.toByteArray();
  }
  
  private void writeEntries(LogArchiveWriter writer) throws IOException {
    writer.putNextEntry(new LogArchiveWriter.ArchiveEntry("file1", date1));
    writer.write("content1".getBytes());
    writer.closeEntry();
    writer.putNextEntry(new LogArchiveWriter.ArchiveEntry("file2", date2));
    writer.write("content2".getBytes());
    writer.closeEntry();
    writer.close();
  }
  
}
