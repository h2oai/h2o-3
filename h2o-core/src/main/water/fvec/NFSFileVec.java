package water.fvec;

import java.io.File;

import water.*;
import water.persist.PersistNFS;

// A distributed file-backed Vector
//
public class NFSFileVec extends FileVec {
  static int DEBUG_WEAVER;

  // Make a new NFSFileVec key which holds the filename implicitly.
  // This name is used by the DVecs to load data on-demand.
  public static NFSFileVec make(File f) {
    Futures fs = new Futures();
    NFSFileVec nfs = make(f, fs);
    fs.blockForPending();
    return nfs;
  }

  public static NFSFileVec make(File f, Futures fs) {
    long size = f.length();
    Key k = Vec.newKey(PersistNFS.decodeFile(f));
    // Insert the top-level FileVec key into the store
    NFSFileVec nfs = new NFSFileVec(k,size);
    DKV.put(k,nfs,fs);
    return nfs;
  }

  private NFSFileVec(Key key, long len) {super(key,len,Value.NFS);}
}
