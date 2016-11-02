package water.udf;

import water.fvec.Vec;
import water.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This column depends a plurality of columns
 */
public class UnfoldingColumn<X, Y> implements Column<List<Y>> {
  private final Unfoldable<X, Y> f;
  private final Column<X> column;
  private int requiredSize;

  @Override public Vec vec() { return new VirtualVec(this); }

  @Override public int rowLayout() { return column.rowLayout(); }
  
  public UnfoldingColumn(Unfoldable<X, Y> f, Column<X> column) {
    this.f = f;
    this.column = column;
    this.requiredSize = 0;
  }

  public UnfoldingColumn(Unfoldable<X, Y> f, Column<X> column, int requiredSize) {
    this.f = f;
    this.column = column;
    this.requiredSize = requiredSize;
  }
  
  @Override public List<Y> get(long idx) {
    List<Y> raw = isNA(idx) ? Collections.<Y>emptyList() : f.apply(column.get(idx));
    if (requiredSize == 0 || raw.size() == requiredSize) return raw;
    else {
      List<Y> result = raw.subList(0, Math.min(raw.size(), requiredSize));
      if (result.size() < requiredSize) {
        List<Y> fullResult = new ArrayList<>(requiredSize);
        fullResult.addAll(result);
        for (int i = result.size(); i < requiredSize; i++) {
          fullResult.add(null);
        }
      }
      return result;
    }
  }

  @Override public boolean isNA(long idx) {
    return column.isNA(idx);
  }

  @Override
  public String getString(long idx) { 
    return StringUtils.join(", ", get(idx)); 
  }
}
