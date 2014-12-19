package water;

import java.util.Arrays;
import water.init.JarHash;

/**
 * Struct holding H2ONode health info.
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 */
public class HeartBeat extends Iced<HeartBeat> {
  int _hb_version;
  int _cloud_hash;       // Cloud-membership hash
  boolean _common_knowledge; // Cloud shares common knowledge
  public boolean _client;    // This is a client node
  char _cloud_size;      // Cloud-size this guy is reporting
  long _jvm_boot_msec;   // Boot time of JVM
  public long jvmBootTimeMsec(){return _jvm_boot_msec;}
  byte[] _jar_md5;       // JAR file digest
  public char _num_cpus; // Number of CPUs for this Node, limit of 65535
  public double _gflops;        // Number of GFlops for this node
  public double _membw;         // Memory bandwidth in GB/s
  public float _system_load_average;
  long _system_idle_ticks;
  long _system_total_ticks;
  long _process_total_ticks;
  public int _process_num_open_fds;
  public int _cpus_allowed;   // Number of CPUs allowed by process
  public int _nthreads;
  public String _pid;

  // Scaled by K or by M setters & getters.
  private int _free_mem;         // Free memory in K (goes up and down with GC)
  void set_free_mem (long n) {  _free_mem = (int)(n>>10); }
  public long get_free_mem ()  { return ((long) _free_mem)<<10 ; }
  int _tot_mem;          // Total memory in K (should track virtual mem?)
  void set_tot_mem  (long n) {   _tot_mem = (int)(n>>10); }
  public long get_tot_mem  ()  { return ((long)  _tot_mem)<<10 ; }
  int _max_mem;          // Max memory in K (max mem limit for JVM)
  void set_max_mem  (long n) {   _max_mem = (int)(n>>10); }
  public long get_max_mem  ()  { return ((long)  _max_mem)<<10 ; }
  public int _keys;       // Number of LOCAL keys in this node, cached or homed
  int _mvalsz, _tvalsz; // Sum of value bytes used, in K; m=cached_in_memory, t=total
  void set_mvalsz(long n) { _mvalsz = (int)(n>>10); }
  public long get_mvalsz()  { return ((long)_mvalsz)<<10 ; }
  void set_tvalsz(long n) { _tvalsz = (int)(n>>10); }
  public long get_tvalsz()  { return ((long)_tvalsz)<<10 ; }
  int _free_disk;        // Free disk (internally stored in megabyte precision)
  void set_free_disk(long n) { _free_disk = (int)(n>>20); }
  public long get_free_disk()  { return ((long)_free_disk)<<20 ; }
  int _max_disk;         // Disk size (internally stored in megabyte precision)
  void set_max_disk (long n) {  _max_disk = (int)(n>>20); }
  public long get_max_disk ()  { return  ((long)_max_disk)<<20 ; }

  boolean check_jar_md5() {
    if( H2O.ARGS.md5skip || Arrays.equals(JarHash.JARHASH, _jar_md5) ) return true;
    System.out.println("Jar check fails; my hash="+Arrays.toString(JarHash.JARHASH));
    System.out.println("Jar check fails; received hash="+Arrays.toString(_jar_md5));
    return false;
  }

  public char _rpcs;            // Outstanding DFutureTasks

  // Number of elements & threads in high FJ work queues
  public short _fjthrds[];      // Number of threads (not all are runnable)
  public short _fjqueue[];      // Number of elements in FJ work queue
  public char _tcps_active;     // Threads trying do a TCP send
}
