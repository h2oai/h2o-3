package hex.deeplearning;

import hex.Model;
import water.Key;

/**
 * Simple version of DLM.
 */
public abstract class SimpleDLM extends  Model<DeepLearningModel,DeepLearningModel.DeepLearningParameters,DeepLearningModel.DeepLearningModelOutput> {
  /**
   * Full constructor
   *
   * @param selfKey
   * @param parms
   * @param output
   */
  public SimpleDLM(Key<DeepLearningModel> selfKey, DeepLearningModel.DeepLearningParameters parms, DeepLearningModel.DeepLearningModelOutput output) {
    super(selfKey, parms, output);
  }
//  public SimpleDLM(){}
}
