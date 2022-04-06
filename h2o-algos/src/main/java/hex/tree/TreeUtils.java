package hex.tree;

import hex.KeyValue;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

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

  public static void checkInteractionConstraints(ModelBuilder<?, ?, ?> mb, Frame train, String[][] constraints) {
    for (String[] constraintsSet : constraints) {
      for (String constraint : constraintsSet) {
        if(mb._parms._ignored_columns != null && ArrayUtils.find(mb._parms._ignored_columns, constraint) != -1) {
          mb.error("_interaction_constraints", "Column with the name '" + constraint + "' is set in ignored columns and cannot be used in interaction.");
        } else {
          Vec v = train.vec(constraint);
          if (v == null) {
            mb.error("_interaction_constraints", "Invalid interaction constraint - there is no column '" + constraint + "' in the training frame.");
          }
        }
        if(constraint.equals(mb._parms._response_column)){
          mb.error("'_interaction_constraints'", "Column with the name '" + constraint + "' is used as response column and cannot be used in interaction.");
        }
        if(constraint.equals(mb._parms._weights_column)){
          mb.error("'_interaction_constraints'","Column with the name '" + constraint + "' is used as weights column and cannot be used in interaction.");
        }
        if(constraint.equals(mb._parms._fold_column)){
          mb.error("_interaction_constraints", "Column with the name '" + constraint + "' is used as fold column and cannot be used in interaction.");
        }
      }
    }
  }

  public static int getResponseLevelIndex(final String categorical, final SharedTreeModel.SharedTreeOutput sharedTreeOutput) {
    final String trimmedCategorical = categorical != null ? categorical.trim() : ""; // Trim the categorical once - input from the user

    if (! sharedTreeOutput.isClassifier()) {
      if (!trimmedCategorical.isEmpty())
        throw new IllegalArgumentException("There are no tree classes for " + sharedTreeOutput.getModelCategory() + ".");
      return 0; // There is only one tree for non-classification models
    }

    final String[] responseColumnDomain = sharedTreeOutput._domains[sharedTreeOutput.responseIdx()];
    if (sharedTreeOutput.getModelCategory() == ModelCategory.Binomial) {
      if (!trimmedCategorical.isEmpty() && !trimmedCategorical.equals(responseColumnDomain[0])) {
        throw new IllegalArgumentException("For binomial, only one tree class has been built per each iteration: " + responseColumnDomain[0]);
      } else {
        return 0;
      }
    } else {
      for (int i = 0; i < responseColumnDomain.length; i++) {
        // User is supposed to enter the name of the categorical level correctly, not ignoring case
        if (trimmedCategorical.equals(responseColumnDomain[i]))
          return i;
      }
      throw new IllegalArgumentException("There is no such tree class. Given categorical level does not exist in response column: " + trimmedCategorical);
    }
  }

}
