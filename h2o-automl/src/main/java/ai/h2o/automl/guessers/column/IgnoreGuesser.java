package ai.h2o.automl.guessers.column;


import ai.h2o.automl.colmeta.ColMeta;
import water.fvec.Vec;
import water.util.Log;

public class IgnoreGuesser extends Guesser {
  public IgnoreGuesser(ColMeta cm) { super(cm); }

  public enum IgnoreReason {
    user_specified,
    is_bad,
    is_constant,
    mostly_constant,
    is_string,
    is_uuid,

  }

  @Override public void guess0(String name, Vec v) {
    if( _cm._response ) return; // dont care
    _cm._ignored = null!=(ignoreReason(v)); // auto ignore from the outset
    if( _cm._ignored ) {
      _cm._ignoredReason = ignoreReason(v);
      Log.info("AutoML ignoring " + name + " column (Reason: " + _cm._ignoredReason + ")");
    }
  }

  private static IgnoreReason ignoreReason(Vec v) {
    if( v.isBad() ) return IgnoreReason.is_bad;
    if( v.isConst()) return IgnoreReason.is_constant;
    if( v.isString()) return IgnoreReason.is_string;
    if( v.isUUID()) return IgnoreReason.is_uuid;
    return null;
  }
}
