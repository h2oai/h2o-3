package water.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Abstract layer over different types of log containers (zip, txt,...)
 */
public abstract class LogArchiveWriter implements Closeable {

  final OutputStream _os;

  LogArchiveWriter(OutputStream os) {
    _os = os;
  }

  public abstract void putNextEntry(ArchiveEntry entry) throws IOException;

  public abstract void closeEntry() throws IOException;

  public void write(byte[] b, int off, int len) throws IOException {
    _os.write(b, off, len);
  }

  public final void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }
  
  @Override
  public void close() throws IOException {
    _os.close();
  }

  public static class ArchiveEntry {
    final String _name;
    final long _time;

    public ArchiveEntry(String name, Date date) {
      this(name, date.getTime());
    }
    
    ArchiveEntry(String name, long time) {
      _name = name;
      _time = time;
    }

    @Override
    public String toString() {
      return _name + " (" + new Date(_time) + ")";
    }
  }

}

class ZipLogArchiveWriter extends LogArchiveWriter {

  private final ZipOutputStream _zos;

  ZipLogArchiveWriter(OutputStream os) {
    this(new ZipOutputStream(os));
  }

  private ZipLogArchiveWriter(ZipOutputStream zos) {
    super(zos);
    _zos = zos;
  }

  @Override
  public void putNextEntry(ArchiveEntry entry) throws IOException {
    ZipEntry ze = new ZipEntry(entry._name);
    ze.setTime(entry._time);
    _zos.putNextEntry(ze);
  }

  @Override
  public void closeEntry() throws IOException {
    _zos.closeEntry();
  }

}

class ConcatenatedLogArchiveWriter extends LogArchiveWriter {

  ConcatenatedLogArchiveWriter(OutputStream baos) {
    super(baos);
  }

  @Override
  public void putNextEntry(ArchiveEntry entry) throws IOException {
    String entryStr = entry.toString();
    String header = "\n" + entryStr + ":\n" +
            org.apache.commons.lang.StringUtils.repeat("=", entryStr.length() + 1) + "\n";
    _os.write(StringUtils.toBytes(header));
  }

  @Override
  public void closeEntry() {
    // noop
  }

}

