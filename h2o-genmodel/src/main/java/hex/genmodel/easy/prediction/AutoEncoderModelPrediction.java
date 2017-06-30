package hex.genmodel.easy.prediction;

import hex.genmodel.easy.RowData;

/**
 * Data reconstructed by the AutoEncoder model based on a given input.
 */
public class AutoEncoderModelPrediction extends AbstractPrediction {
  /**
   * Representation of the original input the way AutoEncoder model sees it (1-hot encoded categorical values)
   */
  public double[] original;

  /**
   * Reconstructed data, the array has same length as the original input. The user can use the original input
   * and reconstructed output to easily calculate eg. the reconstruction error.
   */
  public double[] reconstructed;

  /**
   * Reconstructed data represented in RowData structure. The structure will copy the structure of the RowData input
   * with the exception of categorical values. Categorical fields will be represented as a map of the domain values
   * to the reconstructed values.
   * Example: input RowData([sex: "Male", ..]) will produce output RowData([sex: [Male: 0.9, Female: 0.1], ..]
   */
  public RowData reconstructedRowData;
}