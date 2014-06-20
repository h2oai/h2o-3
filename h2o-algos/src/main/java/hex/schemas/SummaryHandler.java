package hex.schemas;

import hex.Summary;
import water.H2O;
import water.api.Handler;
import water.fvec.Frame;

public class SummaryHandler extends Handler<SummaryHandler,SummaryV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Inputs
  public Frame _fr;
  public int _max_qbins;

  // Outputs
  public Summary[] _summary;    // The summary

  public SummaryHandler() {}
  public void work() { _summary = Summary.doFrame(_fr,_max_qbins); }
  @Override protected SummaryV2 schema(int version) { return new SummaryV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
