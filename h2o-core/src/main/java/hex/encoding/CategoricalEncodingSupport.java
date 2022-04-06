package hex.encoding;

import hex.ToEigenVec;
import water.fvec.Frame;

public interface CategoricalEncodingSupport {
  CategoricalEncoding.Scheme getCategoricalEncoding();
  int getMaxCategoricalLevels();
  ToEigenVec getToEigenVec();
  default CategoricalEncoder getCategoricalEncoder() { return null; }
//  String[] getNonPredictors();
}
