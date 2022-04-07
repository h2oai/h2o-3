package water.rapids.prims;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Random;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class AstPredictedVsActualByVarTest extends TestUtil {

    @Test
    public void testWithNAsInResponseAndWeights() {
        try {
            Scope.enter();
            Frame train = parseAndTrackTestFile("smalldata/prostate/prostate_cat.csv");
            Vec response = train.vec("CAPSULE");
            final Random rnd = RandomUtils.getRNG(0xCAFE);
            Vec weights = response.makeCon(1);
            Vec weightsZeroedNAs = response.makeCon(1);
            train.add("weight", weights);
            DKV.put(train);
            // set 20% of the response to NA
            for (int i = 0; i < response.length(); i++) {
                double w = rnd.nextDouble();
                weights.set(i, w);
                if (i % 5 == 0) {
                    response.setNA(i);
                    weightsZeroedNAs.set(i, 0);
                } else {
                    weightsZeroedNAs.set(i, w);
                }
            }
            GLMModel.GLMParameters p = new GLMModel.GLMParameters();
            p._train = train._key;
            p._response_column = "CAPSULE";
            p._weights_column = "weight";
            GLMModel m = new GLM(p).trainModel().get();
            Scope.track_generic(m);
            Frame predicted = Scope.track(m.score(train));
            Frame predictedVsActual = Rapids.exec(
                    "(predicted.vs.actual.by.var " + m._key + " " + train._key + " \"DPROS\" " + predicted._key + ")"
            ).getFrame();
            Log.info(predictedVsActual.toTwoDimTable());
            Scope.track(predictedVsActual);
            // GLM will skip rows with NA in response, we shouldn't consider them for calculating averages
            // the level of the variable is effectively not seen because the row is skipped
            Vec varVec = train.vec("DPROS");
            double[] predictedAry = new double[varVec.domain().length];
            double[] actualAry = new double[varVec.domain().length];
            double[] weightsAry = new double[varVec.domain().length];
            for (int i = 0; i < response.length(); i++) {
                if (response.isNA(i))
                    continue;
                int level = (int) varVec.at(i);
                predictedAry[level] += weights.at(i) * predicted.vec(0).at(i);
                actualAry[level] += weights.at(i) * train.vec("CAPSULE").at(i);
                weightsAry[level] += weights.at(i);
            }
            for (int i = 0; i < varVec.domain().length; i++) {
                assertEquals(varVec.domain()[i], predictedVsActual.vec(0).atStr(new BufferedString(), i).toString());
                double pred = predictedVsActual.vec(1).at(i);
                assertEquals(pred, predictedAry[i] / weightsAry[i], 1e-5);
                double act = predictedVsActual.vec(2).at(i);
                assertEquals(act, actualAry[i] / weightsAry[i], 1e-5);
            }
        } finally {
            Scope.exit();
        }
    }
}
