package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;

/**
 * a DataTransformer that never modifies the response column
 */
public abstract class FeatureTransformer<S extends FeatureTransformer> extends DataTransformer<S> {
  
  private String[] _excluded_columns;

  public void excludeColumns(String[] columns) {
    _excluded_columns = columns;
  }
}
