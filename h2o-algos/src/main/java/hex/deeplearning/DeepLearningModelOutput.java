package hex.deeplearning;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.Key;
import water.util.TwoDimTable;

/**
 * The Deep Learning model output contains a few extra fields in addition to the metrics in Model.Output
 * 1) Scoring history (raw data)
 * 2) weights/biases (raw data)
 * 3) variable importances (TwoDimTable)
 */
public class DeepLearningModelOutput extends Model.Output {
  public DeepLearningModelOutput(DeepLearning b) {
    super((ModelBuilder)b);
    autoencoder = b.params()._autoencoder;
    assert b.isSupervised() == !autoencoder;
  }
  final boolean autoencoder;

  @Override
  public boolean isAutoencoder() { return autoencoder; }

  DeepLearningScoringInfo errors;
  Key[] weights;
  Key[] biases;
  double[] normmul;
  double[] normsub;
  double[] normrespmul;
  double[] normrespsub;
  int[] catoffsets;
  public TwoDimTable _variable_importances;

  @Override public ModelCategory getModelCategory() {
    return autoencoder ? ModelCategory.AutoEncoder : super.getModelCategory();
  }

  @Override public boolean isSupervised() {
    return !autoencoder;
  }
} // DeepLearningModelOutput
