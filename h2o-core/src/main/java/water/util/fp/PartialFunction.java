package water.util.fp;
import static water.util.fp.FP.*;

/**
 * Partial function abstraction.
 * A partial function may be not defined on the whole type.
 * It is a function on a subset of domain values. 
 * The subset may be the whole type, or may be even empty.
 * 
 * Created by vpatryshev on 4/10/17.
 */
public abstract class PartialFunction<X, Y> implements Function<X, FP.Option<Y>> {
  
  static <A, B> PartialFunction<A,B> fromFunction(final Function<A,B> f) {
    return new PartialFunction<A, B>() {
      @Override public FP.Option<B> apply(A a) {
        try                 { return Some(f.apply(a)); } 
        catch (Throwable t) { return none(); }
      }
    };
  }
}
