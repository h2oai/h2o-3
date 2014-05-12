package water.util;

import java.io.File;

import water.Key;
import water.fvec.*;
import water.parser.*;

public class FrameUtils {
  /** Parse given file into the form of frame represented by the given key.
   *
   * @param okey  destination key for parsed frame
   * @param file  file to parse
   * @return a new frame
   */
  public static Frame parseFrame(Key okey, File file) {
    if( !file.exists() )
      throw new RuntimeException("File not found " + file);
    if(okey == null)
      okey = Key.make(file.getName());
    Key fkey = NFSFileVec.make(file)._key;
    return ParseDataset2.parse(okey, fkey);
  }
}
