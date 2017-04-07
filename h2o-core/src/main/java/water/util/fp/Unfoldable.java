package water.util.fp;

import java.util.List;

/**
 * Takes a value of type X, produces a multitude of values of type Y
 */
public interface Unfoldable<X, Y> extends Function<X, List<Y>> {}
