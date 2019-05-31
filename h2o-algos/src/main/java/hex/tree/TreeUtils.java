package hex.tree;

import hex.KeyValue;
import hex.ModelBuilder;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.HashSet;
import java.util.Set;

public class TreeUtils {

  public static void checkMonotoneConstraints(ModelBuilder<?, ?, ?> mb, Frame train, KeyValue[] constraints) {
    // we check that there are no duplicate definitions and constraints are defined only for numerical columns
    Set<String> constrained = new HashSet<>();
    for (KeyValue constraint : constraints) {
      if (constrained.contains(constraint.getKey())) {
        mb.error("_monotone_constraints", "Feature '" + constraint.getKey() + "' has multiple constraints.");
        continue;
      }
      constrained.add(constraint.getKey());
      Vec v = train.vec(constraint.getKey());
      if (v == null) {
        mb.error("_monotone_constraints", "Invalid constraint - there is no column '" + constraint.getKey() + "' in the training frame.");
      } else if (v.get_type() != Vec.T_NUM) {
        mb.error("_monotone_constraints", "Invalid constraint - column '" + constraint.getKey() +
                "' has type " + v.get_type_str() + ". Only numeric columns can have monotonic constraints.");
      }
    }
  }

}
