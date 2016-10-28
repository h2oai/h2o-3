package water.udf;

/**
 * This column depends on three other columns
 */
public class Fun3Column<X, Y, Z, T> implements Column<T> {
  private final Function3<X, Y, Z, T> f;
  private final Column<X> xs;
  private final Column<Y> ys;
  private final Column<Z> zs;
  
  public Fun3Column(Function3<X, Y, Z, T> f, Column<X> xs, Column<Y> ys, Column<Z> zs) {
    this.f = f;
    this.xs = xs;
    this.ys = ys;
    this.zs = zs;
  }
  
  @Override public T get(long idx) { 
    return isNA(idx) ? null : f.apply(xs.get(idx), ys.get(idx), zs.get(idx)); 
  }

  @Override public boolean isNA(long idx) { return xs.isNA(idx) || ys.isNA(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(get(idx)); }
}
