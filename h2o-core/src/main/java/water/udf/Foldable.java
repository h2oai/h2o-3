package water.udf;

/**
 * Represents a folding operation applicable to streams or collection
 * 
 * Initial value of type Y is the value that is returned on an empty collection.
 * Apply is used on a pair of values to produce the next value.
 * Apply takes a value of argument type X and a value of result type Y.
 * 
 * Having this, you can define reduction on a collection or a stream. 
 * This is the core of map/reduce.
 * 
 * @see <a href="https://en.wikipedia.org/wiki/Fold_(higher-order_function)">wikipedia</a> for details.
 */
public interface Foldable<X, Y> extends JustCode {
  Y initial();
  Y apply(Y y, X x);
}
