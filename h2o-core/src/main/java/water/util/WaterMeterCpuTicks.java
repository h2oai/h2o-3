package water.util;

import water.*;

public class WaterMeterCpuTicks extends Iced {
  // Input
  public int nodeidx;

  // Output
  public long[][] cpu_ticks;

  public void doIt() {
    H2ONode node = H2O.CLOUD._memary[nodeidx];
    // Synchronous RPC call to get ticks from remote (possibly this) node.
    cpu_ticks = new RPC<>(node, new GetTicksTask()).call().get()._cpuTicks;
  }

  private static class GetTicksTask extends DTask<GetTicksTask> {
    GetTicksTask() { super(H2O.GUI_PRIORITY); }
    private long[][] _cpuTicks;
    @Override public void compute2() {
      // In the case where there isn't any tick information, the client
      // receives a json response object containing an array of length 0;
      // e.g. { cpuTicks: [] }
      _cpuTicks = LinuxProcFileReader.refresh() ? LinuxProcFileReader.getCpuTicks() : new long[0][0];
      tryComplete();
    }
  }
}
