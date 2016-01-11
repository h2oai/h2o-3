package water.util;

import hex.Transformer;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class DCTTransformer extends Transformer<Frame> {
  /** Input dataset to transform */
  public Frame _dataset;
  /** Dimensions of the input array (3 ints) Length, Width, Depth  */
  int[] _dimensions = null;
  /** Whether to do inverse DCT */
  final boolean _inverse = false;

  public DCTTransformer(Key<Frame> dest) {
    super(dest, Frame.class.getName(), "DCTTransformer job");
  }

  @Override protected Job<Frame> execImpl() {
    if (_dimensions.length != 3) 
      throw new H2OIllegalArgumentException("Need 3 dimensions (width/height/depth): WxHxD (1D: Wx1x1, 2D: WxHx1, 3D: WxHxD)");
    if (ArrayUtils.minValue(_dimensions) < 1)
      throw new H2OIllegalArgumentException("Dimensions must be >= 1");
    if( _dataset == null ) throw new H2OIllegalArgumentException("Missing dataset");
    if (_dataset.numCols() < _dimensions[0] * _dimensions[1] * _dimensions[2])
      throw new H2OIllegalArgumentException("Product of dimensions WxHxD must be <= #columns (" + _dataset.numCols() + ")");
    MathUtils.DCT.initCheck(_dataset, _dimensions[0], _dimensions[1], _dimensions[2]);

    return _job.start(
            new H2O.H2OCountedCompleter() {
              @Override
              public void compute2() {
                Frame fft;
                if (_dimensions[1] == 1 && _dimensions[2] == 1) {
                  fft = MathUtils.DCT.transform1D(_dataset, _dimensions[0], _inverse);
                } else if (_dimensions[2] == 1) {
                  fft = MathUtils.DCT.transform2D(_dataset, _dimensions[0], _dimensions[1], _inverse);
                } else {
                  fft = MathUtils.DCT.transform3D(_dataset, _dimensions[0], _dimensions[1], _dimensions[2], _inverse);
                }
                Frame dest = new Frame(_job._result, fft.names(), fft.vecs());
                DKV.put(dest);
                tryComplete();
              }
            }, 1);
  }
}
