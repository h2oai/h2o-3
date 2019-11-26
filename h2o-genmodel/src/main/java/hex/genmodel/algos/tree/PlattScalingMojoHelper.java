package hex.genmodel.algos.tree;

import static hex.genmodel.GenModel.GLM_logitInv;

public class PlattScalingMojoHelper {

    public interface MojoModelWithCalibration {
        double[] getCalibGlmBeta();
    }

    public static boolean calibrateClassProbabilities(MojoModelWithCalibration model, double[] preds) {
        if (model.getCalibGlmBeta() == null)
            return false;
        double p = GLM_logitInv((preds[1] * model.getCalibGlmBeta()[0]) + model.getCalibGlmBeta()[1]);
        preds[1] = 1 - p;
        preds[2] = p;
        return true;
    }

}
