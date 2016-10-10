package water.util;

import com.google.common.base.Function;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Encapsulates a (google) function, so we can pass it around in MoveableCode.
 */
public class Closure<From, To> extends MoveableCode<Function<From, To>> implements Function<From, To> {
  private final   int arity;

  Closure(Function<From, To> instance, int arity) throws IOException {
    super(instance);
    this.arity = arity;
  }

  public static <From, To> Closure<From, To> enclose(Function<From, To> f) throws IOException {
    return (f instanceof Closure) ? (Closure<From, To>)f : new Closure<>(f, 1);
  }

  public static <From, To> Closure<From[], To> enclose(Function<From[], To> f, int arity) throws IOException {
    return (f instanceof Closure) ? (Closure<From[], To>)f : new Closure<>(f, arity);
  }

  @Nullable
  @Override
  public To apply(@Nullable From input) {
    return instance().apply(input);
  }
}
