package hex.deeplearning;

import hex.ScoringInfo;
import water.AutoBuffer;

/**
 * Lightweight DeepLearning scoring history.
 */
public class DeepLearningScoringInfo extends ScoringInfo {
  public int iterations;
  public double epoch_counter;
  public double training_samples;
  public long score_training_samples;
  public long score_validation_samples;

  DeepLearningScoringInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (DeepLearningScoringInfo) new DeepLearningScoringInfo().read(ab);
  }
}
