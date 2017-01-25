package hex.genmodel.easy.prediction;

import hex.genmodel.easy.RowData;

public class AutoEncoderModelPrediction extends AbstractPrediction {
  public double[] original;
  public double[] reconstructed;
  public RowData reconstructedRowData;
}