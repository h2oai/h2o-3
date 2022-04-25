package hex;

import water.fvec.Frame;

public interface DataTransformer {
  
  enum Stage {
    Training,
    Validation,
    Scoring
  }

  /**
   * Applies a transformation on the given frame
   * @param fr
   * @param stage
   * @param params
   * @return
   */
  Frame transform(Frame fr, Stage stage, DataTransformSupport params);
  
  default Frame transform(Frame fr) {
    return transform(fr, Stage.Scoring, null);
  }
  
  default void remove() {};
}
