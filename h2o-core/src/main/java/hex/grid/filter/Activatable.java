package hex.grid.filter;

import hex.Model;

public interface Activatable<MP extends Model.Parameters> {

  /**
   * Provides a way to set implementation into a state when it is considered to be already used. Useful for stateful implementations 
   * as stateless ones do not have any state.
   */
  void activate(MP permutation);
}
