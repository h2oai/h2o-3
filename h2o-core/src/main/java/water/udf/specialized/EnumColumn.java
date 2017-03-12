package water.udf.specialized;

import water.fvec.Vec;
import water.udf.DataColumn;
import water.udf.UnfoldingColumn;
import water.udf.UnfoldingFrame;
import water.udf.fp.PureFunctions;
import water.util.FrameUtils;

import java.util.ArrayList;

import static water.udf.specialized.Integers.Integers;

/**
 * Specialized class for enum columns. 
 * The difference with other columns is that it keeps its domain.
 * 
 * Created by vpatryshev on 2/10/17.
 */

public class EnumColumn extends Integers.Column {
  private final String[] domain;
  /**
   * deserialization :(
   */
  public EnumColumn() { domain = null; }

  private EnumColumn(Vec v, Enums factory) {
    super(v, factory);
    if (factory.domain == null) throw new IllegalArgumentException("Domain missing");
    domain = factory.domain;
    assert domain != null && domain.length > 0 : "Need a domain for enums";
  }
  
  public EnumColumn(Vec v) {
    this(v, Enums.enums(v.domain()));
    if (domain == null) throw new IllegalArgumentException("Domain missing");
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
  
  public UnfoldingColumn<Integer, Integer> oneHotEncode() {
    return new UnfoldingColumn<>(PureFunctions.oneHotEncode(domain), this, domain.length + 1);
  }
  
  public UnfoldingFrame<Integer, DataColumn<Integer>> oneHotEncodedFrame(String colName) {
    if (domain == null) throw new IllegalArgumentException("Domain missing");
    int width = domain.length + 1;
    UnfoldingColumn<Integer, Integer> column = oneHotEncode();
    UnfoldingFrame<Integer, DataColumn<Integer>> frame = new UnfoldingFrame<>(Integers, column.size(), column, width);
    String prefix = frame.uniquify(colName);
    ArrayList<String> colNames = new ArrayList<>(width);
    for (String name : domain) colNames.add(FrameUtils.compoundName(prefix, name));
    colNames.add(FrameUtils.compoundName(prefix, FrameUtils.SUFFIX_FOR_NA));
    frame._names = colNames.toArray(new String[0]);
    return frame;
  }
}
