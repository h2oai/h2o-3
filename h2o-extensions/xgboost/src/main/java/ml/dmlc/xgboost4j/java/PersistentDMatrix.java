package ml.dmlc.xgboost4j.java;

import water.Iced;

/**
 * Implementation of DMatrix that doesn't automatically dispose of the XGB native data when the object gets finalized
 */
public class PersistentDMatrix extends DMatrix {

  private PersistentDMatrix(long handle) {
    super(handle);
  }

  public Wrapper wrap() {
    return new Wrapper(this);
  }

  @Override
  protected void finalize() {
    // Forget the handle but do not destroy the data - memory needs to be explicitly released by calling dispose()
    handle = 0;
    super.finalize();
  }

  /**
   * Turn a DMatrix into a persisted one
   * @param matrix matrix to be marked as persisted, this object will not be usable after the persist finishes
   * @return instance of PersistentDMatrix or null if the input matrix is null
   */
  public static PersistentDMatrix persist(DMatrix matrix) {
    if (matrix == null)
      return null;

    long handle = matrix.handle;
    matrix.handle = 0;
    return new PersistentDMatrix(handle);
  }

  public static class Wrapper extends Iced<Wrapper> {
    private long _handle;

    public Wrapper(PersistentDMatrix matrix) {_handle = matrix.handle; }

    public PersistentDMatrix get() {
      return new PersistentDMatrix(_handle);
    }

    @Override public boolean equals( Object o ) {
      return o instanceof Wrapper && ((Wrapper) o)._handle == _handle;
    }
    @Override public int hashCode() {
      return (int)(_handle ^ (_handle >>> 32));
    }
    @Override public String toString() {
      return Long.toString(_handle);
    }
  }

}
