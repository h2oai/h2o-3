package hex.genmodel;

import java.io.File;
import java.io.IOException;

public class TmpMojoReaderBackend extends ZipfileMojoReaderBackend {

  File _tempZipFile;

  public TmpMojoReaderBackend(File tempZipFile) throws IOException {
    super(tempZipFile.getPath());
    _tempZipFile = tempZipFile;
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (_tempZipFile != null) {
      File f = _tempZipFile;
      _tempZipFile = null; // we don't want to attempt to delete the file twice (even if the first attempt fails)
      if (! f.delete())
        throw new IOException("Failed to delete temporary file " + f);
    }
  }

}
