package hex.encoding;

import hex.ToEigenVec;
import water.fvec.Frame;

public interface CategoricalEncodingSupport {
  CategoricalEncoding.Scheme getCategoricalEncoding();
  int getMaxCategoricalLevels();
  ToEigenVec getToEigenVec();
//  Frame getTrainingFrame();
}
