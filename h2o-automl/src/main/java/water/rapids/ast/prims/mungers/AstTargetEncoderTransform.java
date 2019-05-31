package water.rapids.ast.prims.mungers;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import water.DKV;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstBuiltin;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstStr;
import water.rapids.ast.params.AstStrList;
import water.rapids.vals.ValFrame;

import java.util.HashMap;
import java.util.Map;

/**
 * Rapids wrapper for java TargetEncoder (transform part)
 */
public class AstTargetEncoderTransform extends AstBuiltin<AstTargetEncoderTransform> {
  @Override
  public String[] args() {
    return new String[]{"encodingMapKeys encodingMapFrames frameToTransform teColumns strategy targetColumnName foldColumnName withBlending inflectionPoint smoothing noise seed"};
  }

  @Override
  public String str() {
    return "target.encoder.transform";
  }

  @Override
  public int nargs() {
    return 1 + 12;
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {

    String[] encodingMapKeys = getEncodingMapKeys(env, stk, asts);
    Frame[] encodingMapFrames = getEncodingMapFrames(env, stk, asts);
    Frame frame = getFrameToTransform(env, stk, asts);
    String[] teColumnsToEncode = getTEColumns(env, stk, asts);
    byte dataLeakageHandlingStrategy = getDataLeakageHandlingStrategy(env, stk, asts);
    String targetColumnName = getTargetColumnName(env, stk, asts);
    String foldColumnName = getFoldColumnName(env, stk, asts);
    boolean withBlending = getWithBlending(env, stk, asts);

    BlendingParams params = withBlending ? getBlendingParams(env, stk, asts) : null;

    double noise = getNoise(env, stk, asts);
    double seed = getSeed(env, stk, asts);
    boolean withImputationForOriginalColumns = true;

    TargetEncoder tec = params == null ? new TargetEncoder(teColumnsToEncode) : new TargetEncoder(teColumnsToEncode, params);

    Map<String, Frame> encodingMap = reconstructEncodingMap(encodingMapKeys, encodingMapFrames);

    if(noise == -1) {
      return new ValFrame(tec.applyTargetEncoding(frame, targetColumnName, encodingMap, dataLeakageHandlingStrategy,
              foldColumnName, withBlending, withImputationForOriginalColumns, (long) seed));
    } else {
      return new ValFrame(tec.applyTargetEncoding(frame, targetColumnName, encodingMap, dataLeakageHandlingStrategy,
              foldColumnName, withBlending, noise, withImputationForOriginalColumns, (long) seed));
    }
  }

  private BlendingParams getBlendingParams(Env env, Env.StackHelp stk, AstRoot[] asts) {
      double inflectionPoint = getInflectionPoint(env, stk, asts);
      double smoothing = getSmoothing(env, stk, asts);
      return new BlendingParams(inflectionPoint, smoothing);
  }

  private Map<String, Frame> reconstructEncodingMap(String[] encodingMapKeys, Frame[] encodingMapFrames) {
    Map<String,Frame> encodingMap = new HashMap<>();

    assert encodingMapKeys.length == encodingMapFrames.length : "EncodingMap elements are inconsistent";
    for (int i = 0; i < encodingMapKeys.length; i++) {
      encodingMap.put(encodingMapKeys[i], encodingMapFrames[i]);
    }
    return encodingMap;
  }

  //TODO why can't we use stk.track(asts[1].exec(env)).getStrs(); ?
  private String[] getEncodingMapKeys(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return getArgAsStrings(env, stk, asts[1]);
  }

  private Frame[] getEncodingMapFrames(Env env, Env.StackHelp stk, AstRoot asts[]) {
    String[] frameKeys = getArgAsStrings(env, stk, asts[2]);

    Frame[] framesWithEncodings = new Frame[frameKeys.length];
    int i = 0;
    for(String key : frameKeys) {
      framesWithEncodings[i++] = DKV.getGet(key);
    }
    return framesWithEncodings;
  }

  private String[] getArgAsStrings(Env env, Env.StackHelp stk, AstRoot ast) {
    String[] frameKeys;
    if (ast instanceof AstStrList) {
      AstStrList teColumns = ((AstStrList) ast);
      frameKeys = teColumns._strs;
    }  else if( ast instanceof AstStr) {
      String teColumn = stk.track(ast.exec(env)).getStr();
      frameKeys = new String[]{teColumn};
    } else {
      throw new IllegalStateException("Failed to parse ast parameter: " + ast.toString());
    }
    return frameKeys;
  }

  private Frame getFrameToTransform(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[3].exec(env)).getFrame();
  }

  private String[] getTEColumns(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return getArgAsStrings(env, stk, asts[4]);
  }

  private byte getDataLeakageHandlingStrategy(Env env, Env.StackHelp stk, AstRoot asts[]) {
    String strategy = stk.track(asts[5].exec(env)).getStr();
    if(strategy.equals("kfold")){
      return TargetEncoder.DataLeakageHandlingStrategy.KFold;
    } else if (strategy.equals("loo")) {
      return TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
    } else if(strategy.equals("loo")) {
      return TargetEncoder.DataLeakageHandlingStrategy.None;
    }
    else {
      return TargetEncoder.DataLeakageHandlingStrategy.None;
    }
  }

  private String getTargetColumnName(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[6].exec(env)).getStr();
  }

  private String getFoldColumnName(Env env, Env.StackHelp stk, AstRoot asts[]) {
    try {
      String str = stk.track(asts[7].exec(env)).getStr();
      if(str.equals("")) return null;
      return str;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private boolean getWithBlending(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[8].exec(env)).getNum() == 1;
  }

  private double getInflectionPoint(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[9].exec(env)).getNum();
  }

  private double getSmoothing(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[10].exec(env)).getNum();
  }

  private double getNoise(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[11].exec(env)).getNum();
  }

  private double getSeed(Env env, Env.StackHelp stk, AstRoot asts[]) {
    return stk.track(asts[12].exec(env)).getNum();
  }
}
