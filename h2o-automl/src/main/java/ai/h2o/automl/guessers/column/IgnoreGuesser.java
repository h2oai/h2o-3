package ai.h2o.automl.guessers.column;


import ai.h2o.automl.colmeta.ColMeta;
import water.fvec.Vec;
import water.util.Log;

public class IgnoreGuesser extends Guesser {
  public IgnoreGuesser(ColMeta cm) { super(cm); }

  @Override public void guess0(String name, Vec v) {
    if( _cm._response ) return; // dont care
    _cm._ignored = null!=(ignoreReason(v)); // auto ignore from the outset
    if( _cm._ignored ) {
      _cm._ignoredReason = "AutoML ignoring ID column for " + ignoreReason(v);
      Log.info("AutoML ignoring " + name + " column (Reason: " + _cm._ignoredReason + ")");
    }
  }

  private static String ignoreReason(Vec v) {
    if( v.isBad() ) return "is BAD";
    if( v.isConst()) return "is constant";
    if( v.isString()) return "is String";
    if( v.isUUID()) return "is UUID";
    return null;
  }
}
