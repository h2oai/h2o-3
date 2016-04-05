package water.util;

import water.*;

public class WaterMeterCpuTicks extends Iced {
  // Input
  public int nodeidx;

  // Output
  public long[][] cpu_ticks;

  public void doIt() {
    H2ONode node = H2O.CLOUD._memary[nodeidx];
    GetTicksTask ppt = new GetTicksTask();
    Log.trace("GetTicksTask starting to node " + nodeidx + "...");
    // Synchronous RPC call to get ticks from remote (possibly this) node.
    new RPC<>(node, ppt).call().get();
    Log.trace("GetTicksTask completed to node " + nodeidx);
    cpu_ticks = ppt._cpuTicks;
  }

  private static class GetTicksTask extends DTask<GetTicksTask> {
    private long[][] _cpuTicks;

    public GetTicksTask() {
      super(H2O.GUI_PRIORITY);
      _cpuTicks = null;
    }

    @Override public void compute2() {
      LinuxProcFileReader lpfr = new LinuxProcFileReader();
      lpfr.read();
      if (lpfr.valid()) {
        _cpuTicks = lpfr.getCpuTicks();
      }
      else {
        // In the case where there isn't any tick information, the client receives a json
        // response object containing an array of length 0.
        //
        // e.g.
        // { cpuTicks: [] }
        _cpuTicks = new long[0][0];
      }

      tryComplete();
    }
  }
}
