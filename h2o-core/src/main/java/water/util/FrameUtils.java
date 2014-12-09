package water.util;

import java.io.*;
import java.net.URI;

import water.Key;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.persist.Persist;

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

  public static Frame parseFrame(Key okey, URI uri) throws IOException {
    Key ikey = Persist.anyURIToKey(uri);
    if(okey == null) okey = Key.make(uri.toString());
    return water.parser.ParseDataset2.parse(okey, ikey);
  }

  /**
   * Compute a chunk summary (how many chunks of each type, relative size, total size)
   * @param fr
   * @return chunk summary
   */
  public static ChunkSummary chunkSummary(Frame fr) {
    return new ChunkSummary().doAll(fr);
  }

  /** Generate given numbers of keys by suffixing key by given numbered suffix. */
  public static Key[] generateNumKeys(Key mk, int num) { return generateNumKeys(mk, num, "_part"); }
  public static Key[] generateNumKeys(Key mk, int num, String delim) {
    Key[] ks = new Key[num];
    String n = mk!=null ? mk.toString() : "noname";
    String suffix = "";
    if (n.endsWith(".hex")) {
      n = n.substring(0, n.length()-4); // be nice
      suffix = ".hex";
    }
    for (int i=0; i<num; i++) ks[i] = Key.make(n+delim+i+suffix);
    return ks;
  }
}
