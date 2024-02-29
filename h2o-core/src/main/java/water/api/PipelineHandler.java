package water.api;

import hex.pipeline.DataTransformer;
import hex.schemas.DataTransformerV3;

public class PipelineHandler extends Handler {
  
  public DataTransformerV3 fetchTransformer(int version, DataTransformerV3 schema) {
    return (DataTransformerV3) schema.fillFromImpl(getFromDKV("datatransformer_id", schema.key.key(), DataTransformer.class));
  }
}
