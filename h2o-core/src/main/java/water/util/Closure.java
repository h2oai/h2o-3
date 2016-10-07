package water.util;

import com.google.common.base.Function;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Encapsulates a (google) function, so we can pass it around in MoveableCode.
 */
public class Closure<From, To> extends MoveableCode<Function<From, To>> implements Function<From, To> {
  public Closure(Function<From, To> instance) throws IOException {
    super(instance);
  }

  public static <From, To> Closure<From, To> enclose(Function<From, To> f) throws IOException {
    return (f instanceof Closure) ? (Closure<From, To>)f : new Closure<>(f);
  }

  @Nullable
  @Override
  public To apply(@Nullable From input) {
    return instance().apply(input);
  }
}
