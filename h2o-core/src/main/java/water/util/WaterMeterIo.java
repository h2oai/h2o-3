package water.util;

import water.*;
import water.api.API;
import water.api.SchemaV3;
import water.persist.PersistManager;

public class WaterMeterIo extends Iced {

  public static class IoStatsEntry extends SchemaV3<Iced, IoStatsEntry> {
    @API(help="Back end type", direction = API.Direction.OUTPUT)
    public String backend;

    @API(help="Number of store events", direction = API.Direction.OUTPUT)
    public long store_count;

    @API(help="Cumulative stored bytes", direction = API.Direction.OUTPUT)
    public long store_bytes;

    @API(help="Number of delete events", direction = API.Direction.OUTPUT)
    public long delete_count;

    @API(help="Number of load events", direction = API.Direction.OUTPUT)
    public long load_count;

    @API(help="Cumulative loaded bytes", direction = API.Direction.OUTPUT)
    public long load_bytes;
  }

  // Input
  public int nodeidx;

  // Output
  public IoStatsEntry persist_stats[];

  public void doIt(boolean aggregateAllNodes) {
    if (! aggregateAllNodes) {
      doIt(nodeidx);
      return;
    }

    for (int i = 0; i < H2O.CLOUD.size(); i++) {
      WaterMeterIo io = new WaterMeterIo();
      io.doIt(i);
      if (i == 0) {
        persist_stats = new IoStatsEntry[io.persist_stats.length];
        for (int j = 0; j < persist_stats.length; j++) {
          persist_stats[j] = new IoStatsEntry();
          persist_stats[j].backend    = io.persist_stats[j].backend;
        }
      }

      for (int j = 0; j < persist_stats.length; j++) {
        persist_stats[j].store_count += io.persist_stats[j].store_count;
        persist_stats[j].store_bytes += io.persist_stats[j].store_bytes;
        persist_stats[j].delete_count += io.persist_stats[j].delete_count;
        persist_stats[j].load_count += io.persist_stats[j].load_count;
        persist_stats[j].load_bytes += io.persist_stats[j].load_bytes;
      }
    }
  }

  private void doIt(int idx) {
    H2ONode node = H2O.CLOUD._memary[idx];
    GetTask t = new GetTask();
    Log.trace("IO GetTask starting to node " + idx + "...");
    // Synchronous RPC call to get ticks from remote (possibly this) node.
    new RPC<>(node, t).call().get();
    Log.trace("IO GetTask completed to node " + idx);
    persist_stats = t._persist_stats;
  }

  private static class GetTask extends DTask<GetTask> {
    private IoStatsEntry _persist_stats[];

    public GetTask() { super(H2O.MIN_HI_PRIORITY); _persist_stats = null; }

    @Override public void compute2() {
      PersistManager.PersistStatsEntry s[] = H2O.getPM().getStats();

      int[] backendsToQuery = new int[] {Value.NFS, Value.HDFS, Value.S3, Value.ICE};
      _persist_stats = new IoStatsEntry[backendsToQuery.length];
      for (int i = 0; i < _persist_stats.length; i++) {
        int j = backendsToQuery[i];
        _persist_stats[i] = new IoStatsEntry();
        IoStatsEntry dest_e = _persist_stats[i];
        switch (j) {
          case Value.ICE:
            dest_e.backend = "ice";
            break;
          case Value.HDFS:
            dest_e.backend = "hdfs";
            break;
          case Value.S3:
            dest_e.backend = "s3";
            break;
          case Value.NFS:
            dest_e.backend = "local";
            break;
          default:
            throw H2O.fail();
        }
        PersistManager.PersistStatsEntry src_e = s[j];
        dest_e.store_count = src_e.store_count.get();
        dest_e.store_bytes = src_e.store_bytes.get();
        dest_e.delete_count = src_e.delete_count.get();
        dest_e.load_count = src_e.load_count.get();
        dest_e.load_bytes = src_e.load_bytes.get();
      }

      int[] backendsToZeroCheck = new int[] {0, 5, 6, 7};
      for (int j : backendsToZeroCheck) {
        PersistManager.PersistStatsEntry src_e = s[j];
        assert(src_e.store_count.get() == 0);
        assert(src_e.store_bytes.get() == 0);
        assert(src_e.delete_count.get() == 0);
        assert(src_e.load_count.get() == 0);
        assert(src_e.load_bytes.get() == 0);
      }

      tryComplete();
    }
  }
}
