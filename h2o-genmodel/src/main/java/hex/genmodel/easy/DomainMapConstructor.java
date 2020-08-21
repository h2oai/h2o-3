package hex.genmodel.easy;

import hex.genmodel.GenModel;

import java.util.Map;

/**
 *  Create map from input variable domain information.
 */
abstract class DomainMapConstructor {

  protected final GenModel _m;
  protected final Map<String, Integer> _columnNameToIndex;

  DomainMapConstructor(GenModel m, Map<String, Integer> columnNameToIndex) {
    _m = m;
    _columnNameToIndex = columnNameToIndex;
  }

  abstract protected Map<Integer, CategoricalEncoder> create();
}
