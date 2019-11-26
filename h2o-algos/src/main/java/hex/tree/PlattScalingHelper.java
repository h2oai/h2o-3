package hex.tree;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

import static hex.ModelCategory.Binomial;

public class PlattScalingHelper {
    
    public interface ModelBuilderWithCalibration<M extends Model<M , P, O>, P extends Model.Parameters, O extends Model.Output> {
        ModelBuilder getModelBuilder();
        Frame getCalibrationFrame();
        void setCalibrationFrame(Frame f);
    }

    public interface ParamsWithCalibration {
        Model.Parameters getParams();
        Frame getCalibrationFrame();
        boolean calibrateModel();
    }

    public interface OutputWithCalibration {
        ModelCategory getModelCategory();
        GLMModel calibrationModel();
    }
    
    public static void initCalibration(ModelBuilderWithCalibration builder, ParamsWithCalibration parms, boolean expensive) {
        // Calibration
        Frame cf = parms.getCalibrationFrame();  // User-given calibration set
        if (cf != null) {
            if (! parms.calibrateModel())
                builder.getModelBuilder().warn("_calibration_frame", "Calibration frame was specified but calibration was not requested.");
            Frame adaptedCf = builder.getModelBuilder().init_adaptFrameToTrain(cf, "Calibration Frame", "_calibration_frame", expensive);
            builder.setCalibrationFrame(adaptedCf);
        }
        if (parms.calibrateModel()) {
            if (builder.getModelBuilder().nclasses() != 2)
                builder.getModelBuilder().error("_calibrate_model", "Model calibration is only currently supported for binomial models.");
            if (cf == null)
                builder.getModelBuilder().error("_calibrate_model", "Calibration frame was not specified.");
        }
    }
    
    public static <M extends Model<M , P, O>, P extends Model.Parameters, O extends Model.Output> GLMModel buildCalibrationModel(
        ModelBuilderWithCalibration<M, P, O> builder, ParamsWithCalibration parms, Job job, M model
    ) {
        Key<Frame> calibInputKey = Key.make();
        try {
            Scope.enter();
            job.update(0, "Calibrating probabilities");
            Frame calib = builder.getCalibrationFrame();
            Vec calibWeights = parms.getParams()._weights_column != null ? calib.vec(parms.getParams()._weights_column) : null;
            Frame calibPredict = Scope.track(model.score(calib, null, job, false));
            Frame calibInput = new Frame(calibInputKey,
                new String[]{"p", "response"}, new Vec[]{calibPredict.vec(1), calib.vec(parms.getParams()._response_column)});
            if (calibWeights != null) {
                calibInput.add("weights", calibWeights);
            }
            DKV.put(calibInput);

            Key<Model> calibModelKey = Key.make();
            Job calibJob = new Job<>(calibModelKey, ModelBuilder.javaName("glm"), "Platt Scaling (GLM)");
            GLM calibBuilder = ModelBuilder.make("GLM", calibJob, calibModelKey);
            calibBuilder._parms._intercept = true;
            calibBuilder._parms._response_column = "response";
            calibBuilder._parms._train = calibInput._key;
            calibBuilder._parms._family = GLMModel.GLMParameters.Family.binomial;
            calibBuilder._parms._lambda = new double[] {0.0};
            if (calibWeights != null) {
                calibBuilder._parms._weights_column = "weights";
            }

            return calibBuilder.trainModel().get();
        } finally {
            Scope.exit();
            DKV.remove(calibInputKey);
        }
    }

    public static Frame postProcessPredictions(Frame predictFr, Job j, OutputWithCalibration output) {
        if (output.calibrationModel() == null) {
            return predictFr;
        } else if (output.getModelCategory() == Binomial) {
            Key<Job> jobKey = j != null ? j._key : null;
            Key<Frame> calibInputKey = Key.make();
            Frame calibOutput = null;
            try {
                Frame calibInput = new Frame(calibInputKey, new String[]{"p"}, new Vec[]{predictFr.vec(1)});
                calibOutput = output.calibrationModel().score(calibInput);
                assert calibOutput._names.length == 3;
                Vec[] calPredictions = calibOutput.remove(new int[]{1, 2});
                // append calibrated probabilities to the prediction frame
                predictFr.write_lock(jobKey);
                for (int i = 0; i < calPredictions.length; i++)
                    predictFr.add("cal_" + predictFr.name(1 + i), calPredictions[i]);
                return predictFr.update(jobKey);
            } finally {
                predictFr.unlock(jobKey);
                DKV.remove(calibInputKey);
                if (calibOutput != null)
                    calibOutput.remove();
            }
        } else {
            throw H2O.unimpl("Calibration is only supported for binomial models");
        }
    }
}
