package hex.encoding;

import hex.DataTransformSupport;
import hex.ToEigenVec;
import water.fvec.Frame;

public interface CategoricalEncodingSupport extends DataTransformSupport {
  /**
   * 
   * @return
   */
  CategoricalEncoding.Scheme getCategoricalEncoding();

  /**
   * Override this if remaining categorical columns need to be encoded using a default encoder.
   * @return
   */
  default CategoricalEncoding.Scheme getDefaultCategoricalEncoding() {
    return CategoricalEncoding.Scheme.AUTO;
  }
  int getMaxCategoricalLevels();
  ToEigenVec getToEigenVec();
}
