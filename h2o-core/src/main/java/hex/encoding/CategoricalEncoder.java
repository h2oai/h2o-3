package hex.encoding;

import hex.DataTransformSupport;
import hex.DataTransformer;
import water.fvec.Frame;

public interface CategoricalEncoder extends DataTransformer {

  default Frame encode(Frame fr) {
    return encode(fr, new String[0]);
  }
  
  default Frame encode(Frame fr, String[] skippedCols) {
    return encode(fr, skippedCols, Stage.Scoring, null);
  }
  
  Frame encode(Frame fr, String[] skippedCols, Stage stage, DataTransformSupport params);

  @Override
  default Frame transform(Frame fr, Stage stage, DataTransformSupport params) {
    return encode(fr, new String[0], stage, params);
  }
}
