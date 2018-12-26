package hex;

import hex.glm.GLMModel;
import org.junit.Ignore;
import water.Job;
import water.Scope;
import water.fvec.Frame;

// to be able to access package-private methods in GLM
@Ignore
public class GLMHelper {
  
  public static void runBigScore(GLMModel model,
                                 Frame fr, boolean computeMetrics,
                                 boolean makePrediction, Job j) {
    String[] names = model.makeScoringNames();
    String[][] domains = model.makeScoringDomains(fr, computeMetrics, names);

    Frame adaptedFrame = new Frame(fr);
    model.adaptTestForTrain(adaptedFrame, true, computeMetrics);
    Scope.track(adaptedFrame);

    model
            .makeBigScoreTask(domains, names, adaptedFrame, computeMetrics, makePrediction, j, null)
            .doAll(adaptedFrame);
  } 
  
}
