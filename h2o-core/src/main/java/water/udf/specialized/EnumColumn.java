package water.udf.specialized;

import water.fvec.Vec;
import water.udf.DataColumn;

/**
 * Specialized class for enum columns. 
 * The difference with other columns is that it keeps its domain.
 * 
 * Created by vpatryshev on 2/10/17.
 */

public class EnumColumn extends DataColumn<Integer> {
  private final String[] domain;
  /**
   * deserialization :(
   */
  public EnumColumn() { domain = null; }

  EnumColumn(Vec v, Enums factory) {
    super(v, factory);
    domain = factory.domain;
    assert domain != null && domain.length > 0 : "Need a domain for enums";
  }

  @Override
  public Integer get(long idx) {
    return isNA(idx) ? null : (int) vec().at8(idx);
  }

  @Override
  public void set(long idx, Integer value) {
    if (value == null) vec().setNA(idx);
    else vec().set(idx, value);
  }

  public void set(long idx, int value) {
    vec().set(idx, value);
  }
}
