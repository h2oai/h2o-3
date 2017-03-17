package hex.genmodel;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 */
class ZipfileMojoReaderBackend implements MojoReaderBackend, Closeable {
  private ZipFile zf;

  public ZipfileMojoReaderBackend(String archivename) throws IOException {
    zf = new ZipFile(archivename);
  }

  @Override
  public BufferedReader getTextFile(String filename) throws IOException {
    InputStream input = zf.getInputStream(zf.getEntry(filename));
    return new BufferedReader(new InputStreamReader(input));
  }

  @Override
  public byte[] getBinaryFile(String filename) throws IOException {
    ZipEntry za = zf.getEntry(filename);
    if (za == null)
      throw new IOException("Binary file " + filename + " not found");
    byte[] out = new byte[(int) za.getSize()];
    DataInputStream dis = new DataInputStream(zf.getInputStream(za));
    dis.readFully(out);
    return out;
  }

  @Override
  public boolean exists(String filename) {
    return zf.getEntry(filename) != null;
  }

  @Override
  public void close() throws IOException {
    if (zf != null) {
      ZipFile f = zf;
      zf = null;
      f.close();
    }
  }

}
