package ai.h2o.automl.transforms;

import water.Key;
import water.fvec.TransformWrappedVec;
import water.fvec.Vec;
import water.rapids.Rapids;

public class RapidsHack extends Rapids {
  public RapidsHack(String rapidsStr) {
    super(rapidsStr);
  }
}
