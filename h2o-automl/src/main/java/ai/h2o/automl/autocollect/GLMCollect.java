package ai.h2o.automl.autocollect;

import hex.Model;
import hex.ModelBuilder;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import water.H2O;
import water.IcedWrapper;
import water.Key;
import water.fvec.Frame;

import java.util.HashMap;
import java.util.HashSet;

/**
 * GLM differs from GBM/RF/DL in that a single sweep over some known settings of GLM
 * is enough for any particular dataset.
 *
 * These are the settings:
 *     alpha = .99, lambda_search=true
 *     alpha = 0,   lambda_search=true
 *     alpha = 0,   LBFGS solver
 *
 *
 * There are other options for adding additional features via:
 *    1. Bin columns into categoricals
 *    2. Add interactions
 *
 * At this time these extra features are not synthesized for collection on a GLM run.
 *
 *
 * The collection of GLMs runs once per dataset as part of the collectMeta call
 */
public class GLMCollect extends Collector {
  @Override public void collect(int idFrame, Frame fr, Frame test, String[] ignored, String y, long seedSplit, HashSet<String> configs) {
    Frame[] fs=null;
    try {
      if( test==null )
        fs = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(), Key.make()}, SPLITRATIOS.clone(), seedSplit);  // split data

      Frame train = fs==null?fr:fs[0];
      Frame valid = fs==null?test:fs[1];

      GLM[] glms = new GLM[3];
      GLMModel.GLMParameters p;

      // alpha = 1 + lambda search
      glms[0] = (p=genParms(0.99, false, seedSplit, idFrame, configs))==null ? null : new GLM(p);

      // alpha = 0 + lambda search
      glms[1] = (p=genParms(0, false, seedSplit, idFrame, configs))==null ? null: new GLM(p);

      // alpha = 0 + LBFGS solver
      glms[2] = (p=genParms(0, true, seedSplit, idFrame, configs))==null ? null : new GLM(p);

      GLMModel m = null;
      for (GLM mb : glms) {
        if( mb==null ) continue;
        try {
          mb._parms._train = train._key;
          mb._parms._valid = valid._key;
          mb._parms._ignored_columns = ignored;
          mb._parms._response_column = y;
          String configID=getConfigId(mb._parms, idFrame);
          collectJVMSettings(configID);
          startResourceCollection(configID);
          m = mb.trainModel().get();
          stopResourceCollection();
          logScoreHistory(mb, m, configID);
        } catch (Exception ex) {
          ex.printStackTrace();
        } finally {
          stopResourceCollection();
          if (m != null) m.delete();
        }
      }
    } finally {
      if( fs!=null )
        for (Frame f : fs) f.delete();
    }
  }

  private GLMModel.GLMParameters genParms(double alpha, boolean lbfgs, long seedSplit, int idFrame, HashSet<String> configs) {
    String configID;
    GLMModel.GLMParameters p = new GLMModel.GLMParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", idFrame);
    config.put("SplitSeed", seedSplit);
    p._lambda_search = true;
    p._alpha = new double[]{alpha};
    if( lbfgs ) p._solver = GLMModel.GLMParameters.Solver.L_BFGS;
    config.put("alpha", p._alpha[0]);
    config.put("Solver", p._solver.toString());
    config.put("ConfigId", configID = getConfigId(p, idFrame));
    if( configs.contains(configID) ) return null;
    configs.add(configID);
    AutoCollect.pushMeta(config, config.keySet().toArray(new String[config.size()]), "GLMConfig", null);
    return p;
  }
  protected GLMModel.GLMParameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) { throw H2O.unimpl(); }
  @Override protected ModelBuilder makeModelBuilder(Model.Parameters p) { return new GLM((GLMModel.GLMParameters)p); }
  @Override protected String configId(Model.Parameters p, int idFrame) { return getConfigId((GLMModel.GLMParameters)p, idFrame); }
  @Override protected void logScoreHistory(ModelBuilder mb, Model mm, String configID) {
    // for each lambda, generate new configID, log it in the GLMConfig table and continue
    assert mb instanceof GLM;
    assert mm instanceof GLMModel;
    GLMModel m = (GLMModel)mm;
    HashMap<String,Object> sh = newScoreHist();
    for(IcedWrapper[] iw: m._output._scoring_history.getCellValues()) {
      sh.put("ConfigID", configID);
      sh.put("duration",  Double.valueOf( ((String)iw[1].get()).trim().split(" ")[0]));
      sh.put("lambda", iw[3].get());
      sh.put("num_predictors", iw[4].get());
      sh.put("train_deviance", iw[5].get());
      sh.put("test_deviance",  iw[6].get());
      AutoCollect.pushMeta(sh, sh.keySet().toArray(new String[sh.size()]), "GLMScoreHistory", null);
    }
  }

   protected HashMap<String, Object> newScoreHist() {
    HashMap<String, Object> sh = new HashMap<>();
    sh.put("ConfigID","");
    sh.put("duration",       AutoCollect.SQLNAN);
    sh.put("lambda",         AutoCollect.SQLNAN);
    sh.put("num_predictors", AutoCollect.SQLNAN);
    sh.put("test_deviance",  AutoCollect.SQLNAN);
    sh.put("train_deviance", AutoCollect.SQLNAN);
    return sh;
  }

  private static String getConfigId(GLMModel.GLMParameters p, int idFrame) {
    return "glm_"+idFrame+"_"+p._alpha[0]+"_"+p._solver;
  }
}
