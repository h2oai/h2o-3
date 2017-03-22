package water.udf;

import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class ColumnBase<T> extends Iced<ColumnBase<T>> implements Column<T> {

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }

  @Override 
  public boolean isCompatibleWith(Column<?> ys) {
    return vec().isCompatibleWith(ys.vec());
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private long i = 0;

      @Override
      public boolean hasNext() {
        return i < size() - 1;
      }

      @Override
      public T next() {
        if (!hasNext()) throw new NoSuchElementException("size=" + size());
        return apply(i++);
      }
    };
  }
}
