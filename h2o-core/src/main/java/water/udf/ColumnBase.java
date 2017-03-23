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

  /**
   * Produces a value in a column for a given index
   * @param idx absolute index of the value
   * @return the value at idx
   */
  public abstract T get(long idx);

  /**
   * Produces a value in a column for a given index
   * Need it, because it is a Long → T function
   * @param idx absolute index of the value
   * @return the value at idx
   */
  @Override public T apply(long idx) { return get(idx); }

  /**
   * Produces a value in a column for a given index
   * Need it, because it is a Long → T function
   * @param idx absolute index of the value
   * @return the value at idx
   */
  @Override public T apply(Long idx) { return get(idx); }

  /**
   * Checks if this column is compatible with another.
   * Compatibility consists of Vec compatibility (see).
   * @param ys another column
   * @return true iff this column has the same chunk layout as ys
   */
  @Override public boolean isCompatibleWith(Column<?> ys) {
    return vec().isCompatibleWith(ys.vec());
  }

  /**
   * Being an iterable, the class should be able to produce an iterator
   * that iterates over all values. Not very efficient, but it does not
   * matter for small collections. Convenient to use in tests.
   * 
   * @return an iterator that iterates over all values in the column
   */
  @Override public Iterator<T> iterator() {
    return new Iterator<T>() {
      private long i = 0;

      @Override public boolean hasNext() {
        return i < size() - 1;
      }

      @Override public T next() {
        if (!hasNext()) throw new NoSuchElementException("size=" + size());
        return apply(i++);
      }
      
      @Override public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
