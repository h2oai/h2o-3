package water.udf;

import water.H2O;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This column depends a plurality of columns
 */
public class UnfoldingColumn<X, Y> extends FunColumnBase<List<Y>> {
  private final Unfoldable<X, Y> f;
  private final Column<X> column;
  private int requiredSize;
  
  @Override public long size() { return column.size(); }

  @Override public int rowLayout() { return column.rowLayout(); }

  public UnfoldingColumn(Unfoldable<X, Y> f, Column<X> column) {
    super(column);
    this.f = f;
    this.column = column;
    this.requiredSize = 0;
  }

  public UnfoldingColumn(Unfoldable<X, Y> f, Column<X> column, int requiredSize) {
    super(column);
    this.f = f;
    this.column = column;
    this.requiredSize = requiredSize;
  }
  
  public List<Y> get(long idx) {
    List<Y> raw = isNA(idx) ? Collections.<Y>emptyList() : f.apply(column.apply(idx));
    if (requiredSize == 0 || raw.size() == requiredSize) return raw;
    else {
      List<Y> result = raw.subList(0, Math.min(raw.size(), requiredSize));
      if (result.size() < requiredSize) {
        List<Y> fullResult = new ArrayList<>(requiredSize);
        fullResult.addAll(result);
        for (int i = result.size(); i < requiredSize; i++) {
          fullResult.add(null);
        }
        return fullResult;
      }
      return result;
    }
  }

  @Override public List<Y> apply(long idx) { return get(idx); }

  @Override public List<Y> apply(Long idx) { return get(idx); }

  @Override
  public TypedChunk<List<Y>> chunkAt(int i) {
    throw H2O.unimpl("Will have to think how to implement multi-string chunks...");
  }

  @Override public boolean isNA(long idx) {
    return column.isNA(idx);
  }

  @Override
  public String getString(long idx) { 
    return StringUtils.join(", ", apply(idx)); 
  }
}
