package ai.h2o.cascade.core;

/**
 * Transformation step used inside of a {@link WorkFrame}.
 */
public abstract class WorkFrameTransform {
  protected int ioffset;
  protected int ooffset;


  /**
   * Modify the provided row according to your task's needs. The task is
   * expected to find its input at offset {@code ioffset} within the data
   * {@code row}, and write the output at offset {@code offset} to the same
   * row.
   */
  public abstract void transform(double[] row);


  /**
   * Describe the types of the output columns your task wants to create. The
   * returned array cannot be empty. This method will be called only once, so
   * the result need not be cached.
   */
  public abstract byte[] outputTypes();


  final void setup(int inputOffset, int outputOffset) {
    ioffset = inputOffset;
    ooffset = outputOffset;
  }

}
