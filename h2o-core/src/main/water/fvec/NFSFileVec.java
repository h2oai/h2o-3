package water.fvec;

import java.io.File;

import water.*;
import water.persist.PersistNFS;

// A distributed file-backed Vector
//
public class NFSFileVec extends FileVec {

  // Make a new NFSFileVec key which holds the filename implicitly.
  // This name is used by the DVecs to load data on-demand.
  public static Key make(File f) {
    Futures fs = new Futures();
    Key key = make(f, fs);
    fs.blockForPending();
    return key;
  }

  public static Key make(File f, Futures fs) {
    long size = f.length();
    Key k = Vec.newKey(PersistNFS.decodeFile(f));
    // Insert the top-level FileVec key into the store
    DKV.put(k,new NFSFileVec(k,size), fs);
    return k;
  }

  private NFSFileVec(Key key, long len) {super(key,len,Value.NFS);}
}
