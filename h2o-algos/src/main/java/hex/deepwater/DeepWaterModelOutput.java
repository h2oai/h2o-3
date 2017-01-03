package hex.deepwater;

import hex.Model;
import hex.ModelCategory;
import water.util.TwoDimTable;

public class DeepWaterModelOutput extends Model.Output {

  int _nums;
  int _cats;
  int[] _catOffsets;
  double[] _normMul;
  double[] _normSub;
  double[] _normRespMul;
  double[] _normRespSub;
  boolean _useAllFactorLevels;

  /**
   * The Deep Learning model output contains a few extra fields in addition to the metrics in Model.Output
   * 1) Scoring history (raw data)
   * 2) weights/biases (raw data)
   * 3) variable importances (TwoDimTable)
   */
  public DeepWaterModelOutput(DeepWater b) {
    super(b);
    autoencoder = b._parms._autoencoder;
    assert b.isSupervised() == !autoencoder;
  }
  private final boolean autoencoder;

  @Override
  public boolean isAutoencoder() { return autoencoder; }

  DeepWaterScoringInfo errors;
  public TwoDimTable _variable_importances;

  @Override public ModelCategory getModelCategory() {
    return autoencoder ? ModelCategory.AutoEncoder : super.getModelCategory();
  }

  @Override public boolean isSupervised() {
    return !autoencoder;
  }
}