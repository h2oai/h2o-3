package hex.encoding;

import hex.DataTransformSupport;
import hex.ToEigenVec;
import water.fvec.Frame;

public interface CategoricalEncodingSupport extends DataTransformSupport {
  CategoricalEncoding.Scheme getCategoricalEncoding();
  int getMaxCategoricalLevels();
  ToEigenVec getToEigenVec();
  default CategoricalEncoder getCategoricalEncoder() { return null; }
}
