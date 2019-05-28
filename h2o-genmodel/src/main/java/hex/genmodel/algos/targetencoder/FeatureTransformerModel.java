package hex.genmodel.algos.targetencoder;

import hex.genmodel.easy.RowData;

/**
 * Interface for transformer models 
 */
public interface FeatureTransformerModel {

  RowData transform0(RowData data);

}
