package hex.grid.filter;

import hex.grid.HyperSpaceWalker;

public interface Activatable {

  /**
   * Provides a way to set implementation into a state when it is considered to be already used. Useful for stateful implementations 
   * as stateless ones do not have any state.
   */
  public void activate();

  /**
   * Gives an ability for {@link HyperSpaceWalker} to reset its {@code _permutationFilter}'s states
   * as some of those functions might be stateful 
   */
  public void reset();
}
