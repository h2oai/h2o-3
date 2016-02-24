package ai.h2o.automl.autocollect;

import hex.Model;
import hex.ModelBuilder;
import hex.glm.GLM;
import hex.glm.GLMModel;
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
  @Override public void collect(Frame train, Frame valid, int idFrame, long seed, HashSet<String> configs) {
    GLM[] glms = new GLM[3];

    // alpha = 1 + lambda search
    glms[0] = new GLM(genParms(seed, idFrame, train.numCols(), configs));
    glms[0]._parms._alpha= new double[]{0.99};


    // alpha = 0 + lambda search
    glms[1] = new GLM(genParms(seed, idFrame, train.numCols(), configs));
    glms[1]._parms._alpha=new double[]{0};


    // alpha = 0 + LBFGS solver
    glms[2] = new GLM(genParms(seed, idFrame, train.numCols(), configs));
    glms[2]._parms._alpha = new double[]{0};
    glms[2]._parms._solver = GLMModel.GLMParameters.Solver.L_BFGS;

    GLMModel m=null;
      for (GLM mb : glms) {
        try {
          mb._parms._train = train._key;
          mb._parms._valid = valid._key;
          resourceCollector.start();
          m = mb.trainModel().get();
          resourceCollector.interrupt();
          logScoreHistory(mb, m, getConfigId(m._parms, idFrame));
        } catch (Exception ex) {
          ex.printStackTrace();
        } finally {
          if (!resourceCollector.isInterrupted()) resourceCollector.interrupt();
          if( m!=null ) m.delete();
        }
      }
  }

  @Override protected GLMModel.GLMParameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs) {
    String configID;
    GLMModel.GLMParameters p = new GLMModel.GLMParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", idFrame);
    config.put("SplitSeed", seedSplit);
    p._lambda_search = true;
    config.put("ConfigId", configID = getConfigId(p, idFrame));
    if( configs.contains(configID) ) return null;
    configs.add(configID);
    AutoCollect.pushMeta(config, config.keySet().toArray(new String[config.size()]), "GLMConfig", null);
    return p;
  }

  @Override protected void logScoreHistory(ModelBuilder glm, Model m, String configID) {
    // for each lambda, generate new configID, log it in the GLMConfig table and continue
    assert glm instanceof GLM;
    assert   m instanceof GLMModel;

  }

   protected HashMap<String, Object> newScoreHist() {
    HashMap<String, Object> sh = new HashMap<>();
    sh.put("ConfigID","");
    sh.put("duration", "");
    sh.put("lambda", "");
    sh.put("num_predictors","");
    sh.put("test_deviance", "");
    sh.put("train_deviance", "");
    return sh;
  }

  static String getConfigId(GLMModel.GLMParameters p, int idFrame) {
    return "glm_"+idFrame+"_"+p._alpha[0]+"_"+p._lambda[0];
  }
}
