package water.rapids.prims.word2vec;

import hex.word2vec.Word2VecModel;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Converts a word2vec model to a Frame
 */
public class AstWord2VecToFrame extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"model"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (word2vec.to.frame model)

  @Override
  public String str() {
    return "word2vec.to.frame";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Word2VecModel model = (Word2VecModel) stk.track(asts[1].exec(env)).getModel();
    return new ValFrame(model.toFrame());
  }

}
