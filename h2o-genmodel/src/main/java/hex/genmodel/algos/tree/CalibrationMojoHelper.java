package hex.genmodel.algos.tree;

import hex.genmodel.algos.isotonic.IsotonicCalibrator;

import static hex.genmodel.GenModel.GLM_logitInv;

public class CalibrationMojoHelper {

    public interface MojoModelWithCalibration {
        double[] getCalibGlmBeta();
        IsotonicCalibrator getIsotonicCalibrator();
    }

    public static boolean calibrateClassProbabilities(MojoModelWithCalibration model, double[] preds) {
        if (model.getCalibGlmBeta() != null) {
            double p = GLM_logitInv((preds[1] * model.getCalibGlmBeta()[0]) + model.getCalibGlmBeta()[1]);
            preds[1] = 1 - p;
            preds[2] = p;
            return true;
        } else if (model.getIsotonicCalibrator() != null) {
            double p = model.getIsotonicCalibrator().calibrateP1(preds[2]);
            preds[1] = 1 - p;
            preds[2] = p;
            return true;
        }
        return false;
    }

}
