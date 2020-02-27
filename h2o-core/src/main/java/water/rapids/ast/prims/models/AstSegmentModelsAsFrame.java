package water.rapids.ast.prims.models;

import hex.segments.SegmentModels;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstSegmentModelsAsFrame extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"segment_models"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (segment_models_as_frame segment_models_id)

  @Override
  public String str() {
    return "segment_models_as_frame";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    SegmentModels models = (SegmentModels) stk.track(asts[1].exec(env)).getKeyed();
    return new ValFrame(models.toFrame());
  }

}
