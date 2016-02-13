package hex.deeplearning;

import hex.ScoringInfo;
import water.AutoBuffer;

/**
 * Lightweight DeepLearning scoring history.
 */
public class DeepLearningScoringInfo extends ScoringInfo implements ScoringInfo.HasEpochs, ScoringInfo.HasSamples, ScoringInfo.HasIterations
{
  public int iterations;
  public double epoch_counter;
  public double training_samples;
  public long score_training_samples;
  public long score_validation_samples;

  public int iterations() { return iterations; };
  public double epoch_counter() { return epoch_counter; }
  public double training_samples() { return training_samples; }
  public long score_training_samples() { return score_training_samples; }
  public long score_validation_samples() { return score_validation_samples; }

  @Override
  public DeepLearningScoringInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (DeepLearningScoringInfo) new DeepLearningScoringInfo().read(ab);
  }
}
