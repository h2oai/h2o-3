package hex.grid.filter;

import hex.grid.HyperSpaceWalker;

public interface Resetable {

  /**
   * Gives an ability for {@link HyperSpaceWalker} to reset its {@code _permutationFilter}'s states
   * as some of those functions might be stateful
   */
  void reset();
}
