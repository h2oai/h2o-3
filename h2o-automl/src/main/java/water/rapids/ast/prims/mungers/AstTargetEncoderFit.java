package water.rapids.ast.prims.mungers;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstStrList;
import water.rapids.vals.ValMapFrame;

import java.util.Map;

/**
 * Rapids wrapper for java TargetEncoder (fit part)
 *
 * Design:
 *  Due to a stateless nature of the calls from the client we will have to implement target encoding's workflow with two separate calls:
 *  to AstTargetEncoderFit.java and AstTargetEncoderTransform.java
 */
public class AstTargetEncoderFit extends AstBuiltin<AstTargetEncoderFit> {
  @Override
  public String[] args() {
    return new String[]{"trainFrame teColumns targetColumnName foldColumnName"};
  }

  @Override
  public String str() {
    return "target.encoder.fit";
  }

  @Override
  public int nargs() {
    return 1 + 4;
  }

  @Override
  public ValMapFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {

    Frame trainFrame = getTrainingFrame(env, stk, asts);
    String[] teColumnsToEncode = getTEColumns(asts);
    String targetColumnName = getTargetColumnName(env, stk, asts);
    String foldColumnName = getFoldColumnName(env, stk, asts);
    boolean withImputationForOriginalColumns = true; // Default fo now

    // We won't actually use this instance here.  Because we will instantiate another instance of TargetEncoder in the second `transform`  call
    BlendingParams params = new BlendingParams(3, 1);

    TargetEncoder tec = new TargetEncoder(teColumnsToEncode, params);

    Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, targetColumnName, foldColumnName, withImputationForOriginalColumns);

    return new ValMapFrame(encodingMap);
  }

  private Frame getTrainingFrame(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[1].exec(env)).getFrame();
  }

  private String[] getTEColumns(AstRoot asts[]) {

    if (asts[2] instanceof AstStrList) {
      AstStrList teColumns = ((AstStrList) asts[2]);
      return teColumns._strs;
    }
    else throw new IllegalStateException("Couldn't parse `teColumns` parameter");
  }

  private String getTargetColumnName(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[3].exec(env)).getStr();
  }

  private String getFoldColumnName(Env env, Env.StackHelp stk, AstRoot asts[]) {
    try {
      String str = stk.track(asts[4].exec(env)).getStr();
      if(str.equals("")) return null;
      return str;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private boolean getWithImputation(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[5].exec(env)).getNum() == 1;
  }

}
