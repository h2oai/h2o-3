package hex.deepwater;

import hex.ScoringInfo;
import water.AutoBuffer;

/**
 * Lightweight DeepLearning scoring history.
 */
public class DeepWaterScoringInfo extends ScoringInfo implements ScoringInfo.HasEpochs, ScoringInfo.HasSamples, ScoringInfo.HasIterations
{
  int iterations;
  double epoch_counter;
  double training_samples;
  long score_training_samples;
  long score_validation_samples;

  public int iterations() { return iterations; }
  public double epoch_counter() { return epoch_counter; }
  public double training_samples() { return training_samples; }
  public long score_training_samples() { return score_training_samples; }
  public long score_validation_samples() { return score_validation_samples; }
}
