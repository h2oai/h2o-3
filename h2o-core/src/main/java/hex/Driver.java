package hex;

import water.H2O;
import water.Scope;

/**
 * Created by vpatryshev on 12/29/16.
 */
abstract public class Driver extends H2O.H2OCountedCompleter<Driver> {
  private ModelBuilder modelBuilder;

  protected Driver(ModelBuilder modelBuilder) {
    super();
    this.modelBuilder = modelBuilder;
  }

  protected Driver(ModelBuilder modelBuilder, H2O.H2OCountedCompleter completer) {
    super(completer);
    this.modelBuilder = modelBuilder;
  }

  // Pull the boilerplate out of the computeImpl(), so the algo writer doesn't need to worry about the following:
  // 1) Scope (unless they want to keep data, then they must call Scope.untrack(Key<Vec>[]))
  // 2) Train/Valid frame locking and unlocking
  // 3) calling tryComplete()
  public void compute2() {
    try {
      Scope.enter();
      modelBuilder._parms.read_lock_frames(modelBuilder._job); // Fetch & read-lock input frames
      computeImpl();
    } finally {
      modelBuilder.setFinalState();
      modelBuilder._parms.read_unlock_frames(modelBuilder._job);
      if (!modelBuilder._parms._is_cv_model) modelBuilder.cleanUp(); //cv calls cleanUp on its own terms
      Scope.exit();
    }
    tryComplete();
  }

  public abstract void computeImpl();
}
