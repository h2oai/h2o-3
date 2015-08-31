package water.util;

import hex.Transformer;
import jsr166y.CountedCompleter;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;

public class DCTTransformer extends Transformer<DCTTransformer> {

  /**
   * Input dataset to transform
   */
  public Frame _dataset;
  /**
   * Destination Frame ID
   */
  public Key<Frame> _destination_frame = Key.make();
  /**
   * Dimensions of the input array (3 ints) Length, Width, Depth
   */
  int[] _dimensions = null;
  /**
   * Whether to do inverse DCT
   */
  boolean _inverse = false;

  public DCTTransformer() {
    this(Key.<DCTTransformer>make());
  }

  public DCTTransformer(Key<DCTTransformer> dest) {
    this(dest, "DCTTransformer job");
  }

  public DCTTransformer(Key<DCTTransformer> dest, String desc) {
    super(dest, desc);
  }

  @Override
  protected DCTTransformer execImpl() {
    if (_dimensions.length != 3)
      error("_dimensions", "Need 3 dimensions (width/height/depth): WxHxD (1D: Wx1x1, 2D: WxHx1, 3D: WxHxD)");
    if (ArrayUtils.minValue(_dimensions) < 1)
      error("_dimensions", "Dimensions must be >= 1");
    if (_dataset.numCols() < _dimensions[0] * _dimensions[1] * _dimensions[2])
      error("_dimensions", "Product of dimensions WxHxD must be <= #columns (" + _dataset.numCols() + ")");
    MathUtils.DCT.initCheck(_dataset, _dimensions[0], _dimensions[1], _dimensions[2]);

    return (DCTTransformer) start(
            new H2O.H2OCountedCompleter() {
              @Override
              protected void compute2() {
                Frame fft;
                if (_dimensions[1] == 1 && _dimensions[2] == 1) {
                  fft = MathUtils.DCT.transform1D(_dataset, _dimensions[0], _inverse);
                } else if (_dimensions[2] == 1) {
                  fft = MathUtils.DCT.transform2D(_dataset, _dimensions[0], _dimensions[1], _inverse);
                } else {
                  fft = MathUtils.DCT.transform3D(_dataset, _dimensions[0], _dimensions[1], _dimensions[2], _inverse);
                }
                Frame dest = new Frame(_destination_frame, fft.names(), fft.vecs());
                DKV.put(dest);
                tryComplete();
              }

              @Override
              public void onCompletion(CountedCompleter caller) {
                done();
              }
            }, 1, true);
  }
}
