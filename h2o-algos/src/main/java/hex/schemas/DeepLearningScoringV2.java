package hex.schemas;

import hex.HitRatio;
import hex.VarImp;
import water.api.*;
import hex.deeplearning.DeepLearningModel.DeepLearningScoring;

public class DeepLearningScoringV2<I extends DeepLearningScoring, S extends DeepLearningScoringV2<I, S>> extends Schema<I, S> {
    @API(help="Epoch Counter", direction=API.Direction.OUTPUT)
    public double epoch_counter;

    @API(help="Training Samples", direction=API.Direction.OUTPUT)
    public long training_samples;

    @API(help="Training Time (milliseconds)", direction=API.Direction.OUTPUT)
    public long training_time_ms;

    // training/validation sets
    @API(help="Score for training samples", direction=API.Direction.OUTPUT)
    public long score_training_samples;

    @API(help="Score for validation samples", direction=API.Direction.OUTPUT)
    public long score_validation_samples;

    @API(help="Variable Importance", direction=API.Direction.OUTPUT)
    VarImpBase variable_importances;

    // classification
    @API(help="Training Error", direction=API.Direction.OUTPUT)
    public double train_err;

    @API(help="Validation Error", direction=API.Direction.OUTPUT)
    public double valid_err;

    @API(help="Confusion matrix for training samples", direction=API.Direction.OUTPUT)
    public ConfusionMatrixBase train_confusion_matrix;

    @API(help="Confusion matrix for validation samples", direction=API.Direction.OUTPUT)
    public ConfusionMatrixBase valid_confusion_matrix;

    @API(help="Training AUC", direction=API.Direction.OUTPUT)
    public AUCBase trainAUC;

    @API(help="Validation AUC", direction=API.Direction.OUTPUT)
    public AUCBase validAUC;

    @API(help="Hit ratio on training data", direction=API.Direction.OUTPUT)
    public HitRatioBase train_hitratio;

    @API(help="Hit ratio on validation data", direction=API.Direction.OUTPUT)
    public HitRatioBase valid_hitratio;

    // regression
    @API(help="Training MSE", direction=API.Direction.OUTPUT)
    public double train_mse;
    @API(help="Validation MSE", direction=API.Direction.OUTPUT)
    public double valid_mse;
}
