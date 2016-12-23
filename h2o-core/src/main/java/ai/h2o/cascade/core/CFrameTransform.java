package ai.h2o.cascade.core;

/**
 * Transformation step used inside of a {@link CFrame}.
 */
public abstract class CFrameTransform {
  protected int ioffset;
  protected int ooffset;
  protected int ilength;
  protected int olength;


  public abstract void transform(double[] row);


  final void setup(int inputOffset, int inputLength, int outputOffset, int outputLength) {
    ioffset = inputOffset;
    ooffset = outputOffset;
    ilength = inputLength;
    olength = outputLength;
  }

}
