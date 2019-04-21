package hex.mojopipeline.rapids;

import water.DKV;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import hex.mojopipeline.MojoPipeline;

public class AstPipelineTransform extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"pipeline", "frame"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (mojo.pipeline.transform pipeline frame allowTimestamps)

  @Override
  public String str() {
    return "mojo.pipeline.transform";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val mojoDataVal = stk.track(asts[1].exec(env));
    Frame mojoDataFrame = mojoDataVal.isFrame() ? mojoDataVal.getFrame() : (Frame) DKV.getGet(mojoDataVal.getStr());
    Frame frame = stk.track(asts[2].exec(env)).getFrame();
    boolean allowTimestamps = stk.track(asts[3].exec(env)).getNum() != 0;

    ByteVec mojoData = (ByteVec) mojoDataFrame.anyVec();
    Frame transformed = new MojoPipeline(mojoData).transform(frame, allowTimestamps);
    return new ValFrame(transformed);
  }

}
