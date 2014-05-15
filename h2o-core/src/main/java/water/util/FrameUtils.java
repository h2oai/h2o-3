package water.util;

import java.io.*;
import water.Key;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

public class FrameUtils {
  /** Parse given file into the form of frame represented by the given key.
   *
   * @param okey  destination key for parsed frame
   * @param file  file to parse
   * @return a new frame
   */
  public static Frame parseFrame(Key okey, File file) throws IOException {
    if( !file.exists() )
      throw new FileNotFoundException("File not found " + file);
    if(okey == null) okey = Key.make(file.getName());
    NFSFileVec nfs = NFSFileVec.make(file);
    return water.parser.ParseDataset2.parse(okey, nfs._key);
  }
}
