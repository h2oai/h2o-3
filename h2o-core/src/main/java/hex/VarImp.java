package hex;

import water.Iced;
import water.util.ArrayUtils;

import java.util.HashMap;
import java.util.Map;

public class VarImp extends Iced {
  final public float[] _varimp; // Variable importance of individual variables, unscaled
  final public String[] _names; // Names of variables.
  public VarImp(float[] varimp, String[] names) { _varimp = varimp; _names = names; }
  // Scaled, so largest value is 1.0
  public float[] scaled_values() { return ArrayUtils.div (_varimp.clone(),ArrayUtils.maxValue(_varimp)); }
  // Scaled so all elements total to 100%
  public float[] summary()       { return ArrayUtils.mult(_varimp.clone(),100.0f/ArrayUtils.sum(_varimp)); }
  public Map<String, Float> toMap() {
    Map<String, Float> varImpMap = new HashMap<>(_varimp.length);
    for (int i = 0; i < _varimp.length; i++) {
      varImpMap.put(_names[i], _varimp[i]);
    }
    return varImpMap;
  }
  public int numberOfUsedVariables() {
    int numberOfUsedVariables = 0;
    for (float varimp : _varimp) {
      if (varimp != 0) {
        numberOfUsedVariables++;
      }
    }
    return numberOfUsedVariables;
  }
}
