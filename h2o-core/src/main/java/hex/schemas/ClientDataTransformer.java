package hex.schemas;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.Key;
import water.fvec.Frame;

public class ClientDataTransformer extends DataTransformer<ClientDataTransformer> {

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    throw new UnsupportedOperationException("this transformer is for client rendering only and does not support `transform`");
  }
}
