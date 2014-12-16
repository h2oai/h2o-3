package hex.api;


import hex.schemas.SplitFrameV2;
import hex.splitframe.SplitFrame;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class SplitFrameBuilderHandler extends ModelBuilderHandler<SplitFrame, SplitFrameV2, SplitFrameV2.SplitFrameParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, SplitFrameV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public SplitFrameV2 validate_parameters(int version, SplitFrameV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
