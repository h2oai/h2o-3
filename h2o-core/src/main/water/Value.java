package water;

import java.io.IOException;
import water.persist.Persist;

public class Value {
  public Key _key=null;
  public final int _max=0;
  byte[] rawMem() { return null; }
  byte[] rawPOJO() { return null; }
  void freePOJO() { }
  boolean isLockable() { return false; }
  void freeMem() { }
  public byte[] memOrLoad() { throw H2O.unimpl(); }
  int type() { throw H2O.unimpl(); }
  public boolean isVec() { throw H2O.unimpl(); }

  Value( Key key, Iced ice ) { _key=key; }
  Value( Key key, String payload ) { _key=key; }

  long _lastAccessedTime;
  
  // ---
  // Backend persistence info.  3 bits are reserved for 8 different flavors of
  // backend storage.  1 bit for whether or not the latest _mem field is
  // entirely persisted on the backend storage, or not.  Note that with only 1
  // bit here there is an unclosable datarace: one thread could be trying to
  // change _mem (e.g. to null for deletion) while another is trying to write
  // the existing _mem to disk (for persistence).  This datarace only happens
  // if we have racing deletes of an existing key, along with racing persist
  // attempts.  There are other races that are stopped higher up the stack: we
  // do not attempt to write to disk, unless we have *all* of a Value, so
  // extending _mem (from a remote read) should not conflict with writing _mem
  // to disk.
  //
  // The low 3 bits are final.
  // The on/off disk bit is strictly cleared by the higher layers (e.g. Value.java)
  // and strictly set by the persistence layers (e.g. PersistIce.java).
  volatile byte _persist; // 3 bits of backend flavor; 1 bit of disk/notdisk
  public final static byte ICE = 1<<0; // ICE: distributed local disks
  public final static byte HDFS= 2<<0; // HDFS: backed by hadoop cluster
  public final static byte S3  = 3<<0; // Amazon S3
  public final static byte NFS = 4<<0; // NFS: Standard file system
  public final static byte TCP = 7<<0; // TCP: For profile purposes, not a storage system
  final static byte BACKEND_MASK = (8-1);
  final static byte NOTdsk = 0<<3; // latest _mem is persisted or not
  final static byte ON_dsk = 1<<3;
  public final void clrdsk() { _persist &= ~ON_dsk; } // note: not atomic
  public final void setdsk() { _persist |=  ON_dsk; } // note: not atomic
  public final boolean isPersisted() { return (_persist&ON_dsk)!=0; }
  public final byte backend() { return (byte)(_persist&BACKEND_MASK); }

  // ---
  // Interface for using the persistence layer(s).
  boolean onICE (){ return (backend()) ==  ICE; }
  boolean onHDFS(){ return (backend()) == HDFS; }
  boolean onNFS (){ return (backend()) ==  NFS; }
  boolean onS3  (){ return (backend()) ==   S3; }
  String nameOfPersist() { return nameOfPersist(backend()); }
  static String nameOfPersist(int x) {
    switch( x ) {
    case ICE : return "ICE";
    case HDFS: return "HDFS";
    case S3  : return "S3";
    case NFS : return "NFS";
    case TCP : return "TCP";
    default  : return null;
    }
  }

  /** Store complete Values to disk */
  void storePersist() throws IOException {
    if( isPersisted() ) return;
    Persist.get(backend()).store(this);
  }

  /** Remove dead Values from disk */
  void removePersist() {
    // do not yank memory, as we could have a racing get hold on to this
    //  free_mem();
    if( !isPersisted() || !onICE() ) return; // Never hit disk?
    clrdsk();  // Not persisted now
    Persist.get(backend()).delete(this);
  }

  final void startRemotePut() { throw H2O.unimpl(); }
  final void lockAndInvalidate(H2ONode target, Futures fs) {throw H2O.unimpl(); }
  final void touch() { throw H2O.unimpl(); }
}
