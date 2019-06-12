package hex.genmodel.algos.targetencoder;

import hex.genmodel.easy.RowData;

/**
 * Interface for transformer models // TODO could we unify approach with Word2VecMojoModel ?
 */
public interface FeatureTransformerModel {

  RowData transform(RowData data);

}
