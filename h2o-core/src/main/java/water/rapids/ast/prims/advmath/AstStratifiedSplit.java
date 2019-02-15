package water.rapids.ast.prims.advmath;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.StratifiedSplit;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.*;

public class AstStratifiedSplit extends AstPrimitive {

  public static final String OUTPUT_COLUMN_NAME = "test_train_split";
  public static final String[] OUTPUT_COLUMN_DOMAIN = new String[]{"train", "test"};

  @Override
  public String[] args() {
    return new String[]{"ary", "test_frac", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (h2o.random_stratified_split y test_frac seed)

  @Override
  public String str() {
    return "h2o.random_stratified_split";
  }


  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frame = stk.track(asts[1].exec(env)).getFrame();
    final double testFrac = asts[2].exec(env).getNum();
    long seed = (long) asts[3].exec(env).getNum();
    // It is just a single column
    if (frame.numCols() != 1)
      throw new IllegalArgumentException("Must give a single column to stratify against. Got: " + frame.numCols() + " columns.");
    Vec stratifyingColumn = frame.anyVec();
    Frame result = new Frame(Key.<Frame>make(),
                             new String[] {OUTPUT_COLUMN_NAME},
                             new Vec[] {StratifiedSplit.split(stratifyingColumn, testFrac, seed, OUTPUT_COLUMN_DOMAIN)}
                             );
    return new ValFrame(result);
  }
}
