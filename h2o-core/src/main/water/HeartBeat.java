package water;

import java.util.Arrays;
import water.init.JarHash;

/**
 * Struct holding H2ONode health info.
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 */
public class HeartBeat extends Iced {
  public int _hb_version;
  public int _cloud_hash;       // Cloud-membership hash?
  public boolean _common_knowledge; // Cloud shares common knowledge
  public char _cloud_size;      // Cloud-size this guy is reporting
  public long _jvm_boot_msec;   // Boot time of JVM
  public byte[] _jar_md5;       // JAR file digest
  public char _num_cpus;        // Number of CPUs for this Node, limit of 65535
  public float _system_load_average;
  public long _system_idle_ticks;
  public long _system_total_ticks;
  public long _process_total_ticks;
  public int _process_num_open_fds;

  // Scaled by K or by M setters & getters.
  private int _free_mem;         // Free memory in K (goes up and down with GC)
  public void set_free_mem (long n) {  _free_mem = (int)(n>>10); }
  public long get_free_mem ()  { return ((long) _free_mem)<<10 ; }
  int _tot_mem;          // Total memory in K (should track virtual mem?)
  public void set_tot_mem  (long n) {   _tot_mem = (int)(n>>10); }
  public long get_tot_mem  ()  { return ((long)  _tot_mem)<<10 ; }
  int _max_mem;          // Max memory in K (max mem limit for JVM)
  public void set_max_mem  (long n) {   _max_mem = (int)(n>>10); }
  public long get_max_mem  ()  { return ((long)  _max_mem)<<10 ; }
  public int _keys;      // Number of LOCAL keys in this node, cached or homed
  int _valsz;            // Sum of value bytes used, in K
  public void set_valsz(long n) { _valsz = (int)(n>>10); }
  public long get_valsz()  { return ((long)_valsz)<<10 ; }
  int _free_disk;        // Free disk (internally stored in megabyte precision)
  public void set_free_disk(long n) { _free_disk = (int)(n>>20); }
  public long get_free_disk()  { return ((long)_free_disk)<<20 ; }
  int _max_disk;         // Disk size (internally stored in megabyte precision)
  public void set_max_disk (long n) {  _max_disk = (int)(n>>20); }
  public long get_max_disk ()  { return  ((long)_max_disk)<<20 ; }

  public boolean check_jar_md5() {
    return Arrays.equals(JarHash.JARHASH, _jar_md5);
  }

  public char _rpcs;            // Outstanding DFutureTasks

  // Number of elements & threads in high FJ work queues
  public short _fjthrds_hi[];
  public short _fjqueue_hi[];
  public char _fjthrds_lo;      // Number of threads (not all are runnable)
  public char _fjqueue_lo;      // Number of elements in FJ work queue
  public char _tcps_active;     // Threads trying do a TCP send
}
