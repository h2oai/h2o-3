package hex.tree;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.isotonic.IsotonicRegression;
import hex.isotonic.IsotonicRegressionModel;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import static hex.ModelCategory.Binomial;

public class CalibrationHelper {

    public enum CalibrationMethod {
        AUTO("auto", -1),
        PlattScaling("platt", 1),
        IsotonicRegression("isotonic", 2);

        private final int _calibVecIdx;
        private final String _id;

        CalibrationMethod(String id, int calibVecIdx) {
            _calibVecIdx = calibVecIdx;
            _id = id;
        }

        private int getCalibratedVecIdx() {
            return _calibVecIdx;
        }
        public String getId() {
            return _id;
        }
    }

    public interface ModelBuilderWithCalibration<M extends Model<M , P, O>, P extends Model.Parameters, O extends Model.Output> {
        ModelBuilder<M, P, O> getModelBuilder();
        Frame getCalibrationFrame();
        void setCalibrationFrame(Frame f);
    }

    public interface ParamsWithCalibration {
        Model.Parameters getParams();
        Frame getCalibrationFrame();
        boolean calibrateModel();
        CalibrationMethod getCalibrationMethod();
        void setCalibrationMethod(CalibrationMethod calibrationMethod);
    }

    public interface OutputWithCalibration {
        ModelCategory getModelCategory();
        Model<?, ?, ?> calibrationModel();
        void setCalibrationModel(Model<?, ?, ?> model);
        default CalibrationMethod getCalibrationMethod() {
            assert isCalibrated();
            return calibrationModel() instanceof IsotonicRegressionModel ?
                    CalibrationMethod.IsotonicRegression : CalibrationMethod.PlattScaling;
        }
        default boolean isCalibrated() {
            return calibrationModel() != null;
        }
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

    public static <M extends Model<M , P, O>, P extends Model.Parameters, O extends Model.Output> Model<?, ?, ?> buildCalibrationModel(
        ModelBuilderWithCalibration<M, P, O> builder, ParamsWithCalibration parms, Job job, M model
    ) {
        final CalibrationMethod calibrationMethod = parms.getCalibrationMethod() == CalibrationMethod.AUTO ?
                CalibrationMethod.PlattScaling : parms.getCalibrationMethod();
        Key<Frame> calibInputKey = Key.make();
        try {
            Scope.enter();
            job.update(0, "Calibrating probabilities");
            Frame calib = builder.getCalibrationFrame();
            Vec calibWeights = parms.getParams()._weights_column != null ? calib.vec(parms.getParams()._weights_column) : null;
            Frame calibPredict = Scope.track(model.score(calib, null, job, false));
            int calibVecIdx = calibrationMethod.getCalibratedVecIdx();
            Frame calibInput = new Frame(calibInputKey,
                new String[]{"p", "response"}, new Vec[]{calibPredict.vec(calibVecIdx), calib.vec(parms.getParams()._response_column)});
            if (calibWeights != null) {
                calibInput.add("weights", calibWeights);
            }
            DKV.put(calibInput);

            final ModelBuilder<?, ?, ?> calibrationModelBuilder;
            switch (calibrationMethod) {
                case PlattScaling:
                    calibrationModelBuilder = makePlattScalingModelBuilder(calibInput, calibWeights != null);
                    break;
                case IsotonicRegression:
                    calibrationModelBuilder = makeIsotonicRegressionModelBuilder(calibInput, calibWeights != null);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported calibration method: " + calibrationMethod);
            }
            return calibrationModelBuilder.trainModel().get();
        } finally {
            Scope.exit();
            DKV.remove(calibInputKey);
        }
    }

    static ModelBuilder<?, ?, ?> makePlattScalingModelBuilder(Frame calibInput, boolean hasWeights) {
        Key<Model> calibModelKey = Key.make();
        Job<?> calibJob = new Job<>(calibModelKey, ModelBuilder.javaName("glm"), "Platt Scaling (GLM)");
        GLM calibBuilder = ModelBuilder.make("GLM", calibJob, calibModelKey);
        calibBuilder._parms._intercept = true;
        calibBuilder._parms._response_column = "response";
        calibBuilder._parms._train = calibInput._key;
        calibBuilder._parms._family = GLMModel.GLMParameters.Family.binomial;
        calibBuilder._parms._lambda = new double[] {0.0};
        if (hasWeights) {
            calibBuilder._parms._weights_column = "weights";
        }
        return calibBuilder;
    }

    static ModelBuilder<?, ?, ?> makeIsotonicRegressionModelBuilder(Frame calibInput, boolean hasWeights) {
        Key<Model> calibModelKey = Key.make();
        Job<?> calibJob = new Job<>(calibModelKey, ModelBuilder.javaName("isotonicregression"), "Isotonic Regression Calibration");
        IsotonicRegression calibBuilder = ModelBuilder.make("isotonicregression", calibJob, calibModelKey);
        calibBuilder._parms._response_column = "response";
        calibBuilder._parms._train = calibInput._key;
        calibBuilder._parms._out_of_bounds = IsotonicRegressionModel.OutOfBoundsHandling.Clip;
        if (hasWeights) {
            calibBuilder._parms._weights_column = "weights";
        }
        return calibBuilder;
    }

    public static Frame postProcessPredictions(Frame predictFr, Job j, OutputWithCalibration output) {
        if (output.calibrationModel() == null) {
            return predictFr;
        } else if (output.getModelCategory() == Binomial) {
            Key<Job> jobKey = j != null ? j._key : null;
            Key<Frame> calibInputKey = Key.make();
            Frame calibOutput = null;
            Frame toUnlock = null;
            try {
                final Model<?, ?, ?> calibModel = output.calibrationModel();
                final int calibVecIdx = output.getCalibrationMethod().getCalibratedVecIdx();
                final String[] calibFeatureNames = calibModel._output.features();
                assert calibFeatureNames.length == 1;
                final Frame calibInput = new Frame(calibInputKey, calibFeatureNames, new Vec[]{predictFr.vec(calibVecIdx)});
                calibOutput = calibModel.score(calibInput);
                final Vec[] calPredictions;
                if (calibModel instanceof GLMModel) {
                    assert calibOutput._names.length == 3;
                    calPredictions = calibOutput.remove(new int[]{1, 2});
                } else if (calibModel instanceof IsotonicRegressionModel) {
                    assert calibOutput._names.length == 1;
                    Vec p1 = calibOutput.remove(0);
                    Vec p0 = new P0Task().doAll(Vec.T_NUM, p1).outputFrame().lastVec();
                    calPredictions = new Vec[]{p0, p1};
                } else 
                    throw new UnsupportedOperationException("Unsupported calibration model: " + calibModel);
                // append calibrated probabilities to the prediction frame
                predictFr.write_lock(jobKey);
                toUnlock = predictFr;
                for (int i = 0; i < calPredictions.length; i++)
                    predictFr.add("cal_" + predictFr.name(1 + i), calPredictions[i]);
                return predictFr.update(jobKey);
            } finally {
                if (toUnlock != null) {
                    predictFr.unlock(jobKey);
                }
                DKV.remove(calibInputKey);
                if (calibOutput != null)
                    calibOutput.remove();
            }
        } else {
            throw H2O.unimpl("Calibration is only supported for binomial models");
        }
    }

    private static class P0Task extends MRTask<P0Task> {
        @Override
        public void map(Chunk c, NewChunk nc) {
            for (int i = 0; i < c._len; i++) {
                if (c.isNA(i))
                    nc.addNA();
                else {
                    double p1 = c.atd(i);
                    nc.addNum(1.0 - p1);
                }
            }
        }
    }

}
