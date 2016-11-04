package water.udf;

import water.fvec.Vec;

/**
 * This column depends on three other columns
 */
public class Fun3Column<X, Y, Z, T> implements Column<T> {
  private final Function3<X, Y, Z, T> f;
  private final Column<X> xs;
  private final Column<Y> ys;
  private final Column<Z> zs;
  
  @Override public Vec vec() { return new VirtualVec(this); }

  @Override public int rowLayout() { return xs.rowLayout(); }

  public Fun3Column(Function3<X, Y, Z, T> f, Column<X> xs, Column<Y> ys, Column<Z> zs) {
    this.f = f;
    this.xs = xs;
    this.ys = ys;
    this.zs = zs;
  }
  
  @Override public T get(long idx) { 
    return isNA(idx) ? null : f.apply(xs.get(idx), ys.get(idx), zs.get(idx)); 
  }

  @Override
  public TypedChunk<T> chunkAt(int i) {
    return new FunChunk(xs.chunkAt(i), ys.chunkAt(i), zs.chunkAt(i));
  }

  @Override public boolean isNA(long idx) { return xs.isNA(idx) || ys.isNA(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(get(idx)); }

  /**
   * Pretends to be a chunk of a column, for distributed calculations.
   * Has type, and is not materialized
   */
  public class FunChunk implements TypedChunk<T> {
    private final TypedChunk<X> cx;
    private final TypedChunk<Y> cy;
    private final TypedChunk<Z> cz;

    public FunChunk(TypedChunk<X> cx, TypedChunk<Y> cy, TypedChunk<Z> cz) {
      this.cx = cx;
      this.cy = cy;
      this.cz = cz;
    }

    @Override public T get(int i) {
      return f.apply(cx.get(i), cy.get(i), cz.get(i));
    }
  }
}
