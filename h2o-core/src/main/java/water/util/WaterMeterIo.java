package water.util;

import water.*;
import water.persist.PersistManager;

public class WaterMeterIo extends Iced {
  public static class IoStatsEntry extends Iced {
    String backend;
    public long storeCount;
    public long storeBytes;
    public long deleteCount;
    public long loadCount;
    public long loadBytes;
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
        persist_stats[j].storeCount  += io.persist_stats[j].storeCount;
        persist_stats[j].storeBytes  += io.persist_stats[j].storeBytes;
        persist_stats[j].deleteCount += io.persist_stats[j].deleteCount;
        persist_stats[j].loadCount   += io.persist_stats[j].loadCount;
        persist_stats[j].loadBytes   += io.persist_stats[j].loadBytes;
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

    public GetTask() {
      _persist_stats = null;
    }

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
        dest_e.storeCount  = src_e.storeCount.get();
        dest_e.storeBytes  = src_e.storeBytes.get();
        dest_e.deleteCount = src_e.deleteCount.get();
        dest_e.loadCount   = src_e.loadCount.get();
        dest_e.loadBytes   = src_e.loadBytes.get();
      }

      int[] backendsToZeroCheck = new int[] {0, 5, 6, 7};
      for (int j : backendsToZeroCheck) {
        PersistManager.PersistStatsEntry src_e = s[j];
        assert(src_e.storeCount.get() == 0);
        assert(src_e.storeBytes.get() == 0);
        assert(src_e.deleteCount.get() == 0);
        assert(src_e.loadCount.get() == 0);
        assert(src_e.loadBytes.get() == 0);
      }

      tryComplete();
    }

    @Override public byte priority() {
      return H2O.MIN_HI_PRIORITY;
    }
  }
}
