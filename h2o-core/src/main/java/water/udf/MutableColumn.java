package water.udf;

/**
 * Mutable version of Column
 */
public interface MutableColumn<T> extends Column<T> {

  void set(long idx, T value);
}
