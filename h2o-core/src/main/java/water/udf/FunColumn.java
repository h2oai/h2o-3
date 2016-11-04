package water.udf;

import water.fvec.Vec;

/**
 * This column depends on another column
 */
public class FunColumn<X, Y> implements Column<Y> {
  private final Function<X, Y> f;
  private final Column<X> xs;

  @Override public Vec vec() { return new VirtualVec(this); }

  @Override public int rowLayout() { return xs.rowLayout(); }

  public FunColumn(Function<X, Y> f, Column<X> xs) {
    this.f = f;
    this.xs = xs;
  }
  
  @Override public TypedChunk<Y> chunkAt(int i) {
    return new FunChunk(xs.chunkAt(i));
  }
  
  @Override public Y get(long idx) { return isNA(idx) ? null : f.apply(xs.get(idx)); }

  @Override public boolean isNA(long idx) { return xs.isNA(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(get(idx)); }

  /**
   * Pretends to be a chunk of a column, for distributed calculations.
   * Has type, and is not materialized
   */
  public class FunChunk implements TypedChunk<Y> {
    private final TypedChunk<X> cx;
  
    public FunChunk(TypedChunk<X> cx) { this.cx = cx; }
  
    @Override public Y get(int i) {
      return f.apply(cx.get(i));
    }
  }
}
