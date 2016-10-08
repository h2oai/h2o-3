package water.fvec;

import java.io.File;

import water.*;
import water.persist.PersistNFS;

/** A NFS distributed file-backed Vector
 *  <p>
 *  Vec will be lazily loaded from the NFS file on-demand.  Each machine is
 *  expected to have the <b>same</b> filesystem view onto a file with the same
 *  byte contents.  Each machine will lazily load only the sections of the file
 *  that are assigned to that machine.  Basically, the file starts striped
 *  across some globally visible file system (e.g. NFS, or just replicated on
 *  local disk) and is loaded into memory - again striped across the machines -
 *  without any network traffic or data-motion.
 *  <p>
 *  Useful to "memory map" into RAM large datafiles, often pure text files.
 */

public class NFSFileVec extends FileVec {
  /** Make a new NFSFileVec key which holds the filename implicitly.  This name
   *  is used by the Chunks to load data on-demand.  Blocking
   *  @return  A NFSFileVec mapped to this file. */
  public static NFSFileVec make(File f) {
    Futures fs = new Futures();
    NFSFileVec nfs = make(f, fs);
    fs.blockForPending();
    return nfs;
  }

  /** Make a new NFSFileVec key which holds the filename implicitly.  This name
   *  is used by the Chunks to load data on-demand.
   *  @return  A NFSFileVec mapped to this file. */
  public static NFSFileVec make(File f, Futures fs) {
    if( !f.exists() ) throw new IllegalArgumentException("File not found: "+f.toString());
    long size = f.length();
    Key k = Vec.newKey(PersistNFS.decodeFile(f));
    // Insert the top-level FileVec key into the store
    NFSFileVec nfs = new NFSFileVec(k,size);
    DKV.put(k,nfs,fs);
    return nfs;
  }

  private NFSFileVec(Key key, long len) {super(key,len,Value.NFS);}

}
