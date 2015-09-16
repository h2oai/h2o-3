package water.util;

import java.io.File;
import java.util.ArrayList;
import water.*;
import water.fvec.*;
import water.persist.PersistNFS;

public class FileIntegrityChecker extends MRTask<FileIntegrityChecker> {
  final String[] _files;        // File names found locally
  final long  [] _sizes;        // File sizes found locally
  int[] _ok;                    // OUTPUT: files which are globally compatible

  @Override public void setupLocal() {
    _ok = new int[_files.length];
    for( int i = 0; i < _files.length; ++i ) {
      File f = new File(_files[i]);
      if( f.exists() && (f.length()==_sizes[i]) )
        _ok[i] = 1;
    }
  }

  @Override public void reduce( FileIntegrityChecker o ) { ArrayUtils.add(_ok,o._ok); }
  @Override public byte priority() { return H2O.GUI_PRIORITY; }

  private void addFolder(File path, ArrayList<File> filesInProgress ) {
    if( !path.canRead() ) return;
    File[] files = path.listFiles();
    if( files != null ) { //path is a dir, and these are the files
      for( File f : files ) {
        if( !f.canRead() ) continue; // Ignore unreadable files
        if( f.length() == 0 ) continue; // Ignore 0-byte files
        if( f.isHidden() && !path.isHidden() )
          continue;             // Do not dive into hidden dirs unless asked
        if (f.isDirectory())
          addFolder(f,filesInProgress);
        else
          filesInProgress.add(f);
      }
    } else if (path.length() > 0) { //path is a non-zero byte file
      filesInProgress.add(path);
    }
  }

  public static FileIntegrityChecker check(File r) {  return new FileIntegrityChecker(r).doAllNodes(); }

  public FileIntegrityChecker(File root) {
    ArrayList<File> filesInProgress = new ArrayList<>();
    addFolder(root,filesInProgress);
    _files = new String[filesInProgress.size()];
    _sizes = new long[filesInProgress.size()];
    for( int i = 0; i < _files.length; ++i ) {
      File f = filesInProgress.get(i);
      _files[i] = f.getAbsolutePath();
      _sizes[i] = f.length();
    }
  }

  public int size() { return _files.length; }

  // Sync this directory with H2O.  Record all files that appear to be visible
  // to the entire cloud, and give their Keys.  List also all files which appear
  // on this H2O instance but are not consistent around the cluster, and Keys
  // which match the directory name but are not on disk.
  public Key syncDirectory(ArrayList<String> files,
                           ArrayList<String> keys,
                           ArrayList<String> fails,
                           ArrayList<String> dels) {

    Futures fs = new Futures();
    Key k = null;
    // Find all Keys which match ...
    for( int i = 0; i < _files.length; ++i ) {
      if( _ok[i] < H2O.CLOUD.size() ) {
        if( fails != null ) fails.add(_files[i]);
      } else {
        File f = new File(_files[i]);
        // Do not call getCanonicalFile - which resolves symlinks - breaks test harness
//        try { f = f.getCanonicalFile(); _files[i] = f.getPath(); } // Attempt to canonicalize
//        catch( IOException ignore ) {}
        k = PersistNFS.decodeFile(f);
        if( files != null ) files.add(_files[i]);
        if( keys  != null ) keys .add(k.toString());
        if( DKV.get(k) != null ) dels.add(k.toString());
        new Frame(k).delete_and_lock(null); // Lock before making the NFS; avoids racing ImportFiles creating same Frame
        NFSFileVec nfs = NFSFileVec.make(f, fs);
        new Frame(k,new String[]{"C1"}, new Vec[]{nfs}).update(null).unlock(null);
      }
    }
    fs.blockForPending();
    return k;
  }
}
