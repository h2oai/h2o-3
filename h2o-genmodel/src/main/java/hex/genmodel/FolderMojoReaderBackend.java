package hex.genmodel;

import java.io.*;

/**
 */
class FolderMojoReaderBackend implements MojoReaderBackend {
  private final String rootFolder;

  public FolderMojoReaderBackend(String folder) {
    this.rootFolder = folder;
  }

  @Override
  public BufferedReader getTextFile(final String filename) throws IOException {
    final File sourceFile = new File(rootFolder, filename);
    final FileReader sourceFileReader = new FileReader(sourceFile);

    return new BufferedReader(sourceFileReader);
  }

  @Override
  public byte[] getBinaryFile(final String filename) throws IOException {
    final File sourceFile = new File(rootFolder, filename);
    final byte[] sourceFileBytes = new byte[(int) sourceFile.length()];

    try (final DataInputStream sourceFileDataInputStream =
                 new DataInputStream(new FileInputStream(sourceFile))) {
      sourceFileDataInputStream.readFully(sourceFileBytes);
      return sourceFileBytes;
    }
  }

  @Override
  public boolean exists(final String filename) {
    return new File(rootFolder, filename).exists();
  }
}