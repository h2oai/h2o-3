package hex.genmodel;

import java.io.*;

/**
 */
class FolderMojoReaderBackend implements MojoReaderBackend {
  private String root;

  public FolderMojoReaderBackend(String folder) {
    root = folder;
  }

  @Override
  public BufferedReader getTextFile(String filename) throws IOException {
    File f = new File(root, filename);
    FileReader fr = new FileReader(f);
    return new BufferedReader(fr);
  }

  @Override
  public byte[] getBinaryFile(String filename) throws IOException {
    File f = new File(root, filename);
    byte[] out = new byte[(int) f.length()];
    DataInputStream dis = new DataInputStream(new FileInputStream(f));
    try {
      dis.readFully(out);
      dis.close();
    } finally {
      try { dis.close(); } catch (IOException e) { /* ignored */ }
    }
    return out;
  }

  @Override
  public boolean exists(String filename) {
    return new File(root, filename).exists();
  }
}
