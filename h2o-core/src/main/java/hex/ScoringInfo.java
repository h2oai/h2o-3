package hex;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.H2O;
import water.Iced;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight scoring history snapshot, for things like displaying the scoring history.
 */
public class ScoringInfo extends Iced<ScoringInfo> {
  public long time_stamp_ms;          //absolute time the model metrics were computed
  public long total_training_time_ms; //total training time until this scoring event (including checkpoints)
  public long total_scoring_time_ms; //total scoring time until this scoring event (including checkpoints)
  public long total_setup_time_ms; //total setup time until this scoring event (including checkpoints)
  public long this_scoring_time_ms;   //scoring time for this scoring event (only)
  public boolean is_classification;
  public boolean is_autoencoder;
  public AUC2 training_AUC;
  public AUC2 validation_AUC;

  public boolean validation;
  public boolean cross_validation;

  public ScoreKeeper scored_train = new ScoreKeeper();
  public ScoreKeeper scored_valid = new ScoreKeeper();
  public ScoreKeeper scored_xval = new ScoreKeeper();

  public VarImp variable_importances;

  public interface HasEpochs{ public double epoch_counter(); }
  public interface HasSamples { public double training_samples(); public long score_training_samples(); public long score_validation_samples(); }
  public interface HasIterations { public int iterations(); }

  /**
   * Add a new ScoringInfo to the given array and return the new array.  Note: this has no side effects.
   * @param scoringInfo
   */
  public static ScoringInfo[] prependScoringInfo(ScoringInfo scoringInfo, ScoringInfo[] scoringInfos) {
    if (scoringInfos == null) {
      return new ScoringInfo[]{ scoringInfo };
    } else {
      ScoringInfo[] bigger = new ScoringInfo[scoringInfos.length + 1];
      System.arraycopy(scoringInfos, 0, bigger, 0, scoringInfos.length);
      bigger[bigger.length - 1] = scoringInfo;
      return bigger;
    }
  }

  /** For a given array of ScoringInfo return an array of the cross-validation, validation or training ScoreKeepers, as available. */
  public static ScoreKeeper[] scoreKeepers(ScoringInfo[] scoring_history) {
    ScoreKeeper[] sk = new ScoreKeeper[scoring_history.length];
    for (int i=0;i<sk.length;++i) {
      sk[i] = scoring_history[i].cross_validation ? scoring_history[i].scored_xval
              : scoring_history[i].validation ? scoring_history[i].scored_valid
              : scoring_history[i].scored_train;
    }
    return sk;
  }

  public double metric(ScoreKeeper.StoppingMetric criterion) {
    switch (criterion) {
      case AUC:               { return cross_validation ? scored_xval._AUC : validation ? scored_valid._AUC : scored_train._AUC; }
      case MSE:               { return cross_validation ? scored_xval._mse : validation ? scored_valid._mse : scored_train._mse; }
      case RMSE:               { return cross_validation ? scored_xval._rmse : validation ? scored_valid._rmse : scored_train._rmse; }
      case MAE:               { return cross_validation ? scored_xval._mae : validation ? scored_valid._mae : scored_train._mae; }
      case RMSLE:               { return cross_validation ? scored_xval._rmsle : validation ? scored_valid._rmsle : scored_train._rmsle; }
      case deviance:          { return cross_validation ? scored_xval._mean_residual_deviance : validation ? scored_valid._mean_residual_deviance : scored_train._mean_residual_deviance; }
      case logloss:           { return cross_validation ? scored_xval._logloss : validation ? scored_valid._logloss : scored_train._logloss; }
      case misclassification: { return cross_validation ? scored_xval._classError : validation ? scored_valid._classError : scored_train._classError; }
      case lift_top_group:    { return cross_validation ? scored_xval._lift : validation ? scored_valid._lift : scored_train._lift; }
      case mean_per_class_error: { return cross_validation ? scored_xval._mean_per_class_error : validation ? scored_valid._mean_per_class_error : scored_train._mean_per_class_error; }
      default:                throw H2O.unimpl("Undefined stopping criterion: " + criterion);
    }
  }

  /**
   * Create a java.util.Comparator which allows us to sort an array of ScoringInfo based
   * on a stopping criterion / metric.  Uses cross-validation or validation metrics if
   * available, otherwise falls back to training metrics.  Understands whether more is
   * better for the given criterion and will order the array so that the best models are
   * last (to fit into the behavior of a model that improves over time)
   * @param criterion scalar model metric / stopping criterion by which to sort
   * @return a Comparator on a stopping criterion / metric
   */
  public static final Comparator<ScoringInfo> comparator(final ScoreKeeper.StoppingMetric criterion) {
    return new Comparator<ScoringInfo>() {
      @Override
      public int compare(ScoringInfo o1, ScoringInfo o2) {
        boolean moreIsBetter = ScoreKeeper.moreIsBetter(criterion);

        if (!moreIsBetter)
          return (int)Math.signum(o2.metric(criterion) - o1.metric(criterion));
        else
          return (int)Math.signum(o1.metric(criterion) - o2.metric(criterion));
      }
    };
  }

  /**
   * Sort an array of ScoringInfo based on a stopping criterion / metric.  Uses
   * cross-validation or validation metrics if available, otherwise falls back to training
   * metrics.  Understands whether more is better for the given criterion and will order
   * the array so that the best models are last
   * @param scoringInfos array of ScoringInfo to sort
   * @param criterion scalar model metric / stopping criterion by which to sort
   */
  public static void sort(ScoringInfo[] scoringInfos, ScoreKeeper.StoppingMetric criterion) {
    if (null == scoringInfos) return;
    if (scoringInfos.length == 0) return;

    // handle StoppingMetric.AUTO
    if (criterion == ScoreKeeper.StoppingMetric.AUTO)
      criterion = scoringInfos[0].is_classification ? ScoreKeeper.StoppingMetric.logloss : scoringInfos[0].is_autoencoder ? ScoreKeeper.StoppingMetric.RMSE : ScoreKeeper.StoppingMetric.deviance;

    Arrays.sort(scoringInfos, ScoringInfo.comparator(criterion));
  }

  /**
   * Create a TwoDimTable to display the scoring history from an array of scoringInfo.
   * @param scoringInfos array of ScoringInfo to render
   * @param hasValidation do we have validation metrics?
   * @param hasCrossValidation do we have cross-validation metrics?
   * @param modelCategory the category for the model or models
   * @param isAutoencoder is the model or are the models autoencoders?
   * @return
   */
  public static TwoDimTable createScoringHistoryTable(ScoringInfo[] scoringInfos, boolean hasValidation, boolean hasCrossValidation, ModelCategory modelCategory, boolean isAutoencoder) {
    boolean hasEpochs = (scoringInfos instanceof HasEpochs[]);
    boolean hasSamples = (scoringInfos instanceof HasSamples[]);
    boolean hasIterations = (scoringInfos instanceof HasIterations[]);
    boolean isClassifier = (modelCategory == ModelCategory.Binomial || modelCategory == ModelCategory.Multinomial
            || modelCategory == ModelCategory.Ordinal);

    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    if (hasSamples) { colHeaders.add("Training Speed"); colTypes.add("string"); colFormat.add("%s"); }
    if (hasEpochs) { colHeaders.add("Epochs"); colTypes.add("double"); colFormat.add("%.5f"); }
    if (hasIterations) { colHeaders.add("Iterations"); colTypes.add("int"); colFormat.add("%d"); }
    if (hasSamples) { colHeaders.add("Samples"); colTypes.add("double"); colFormat.add("%f"); }
    colHeaders.add("Training RMSE"); colTypes.add("double"); colFormat.add("%.5f");
    if (modelCategory == ModelCategory.Regression) {
      colHeaders.add("Training Deviance"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if(modelCategory == ModelCategory.Regression) {
      colHeaders.add("Training MAE"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (isClassifier) {
      colHeaders.add("Training LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (modelCategory == ModelCategory.Binomial) {
      colHeaders.add("Training AUC"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (modelCategory == ModelCategory.Binomial) {
      colHeaders.add("Training Lift"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (isClassifier) {
      colHeaders.add("Training Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if(modelCategory == ModelCategory.AutoEncoder) {
      colHeaders.add("Training MSE"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (hasValidation) {
      colHeaders.add("Validation RMSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (modelCategory == ModelCategory.Regression) {
        colHeaders.add("Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Validation MAE"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (isClassifier) {
        colHeaders.add("Validation LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (modelCategory == ModelCategory.Binomial) {
        colHeaders.add("Validation AUC"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (modelCategory == ModelCategory.Binomial) {
        colHeaders.add("Validation Lift"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (isClassifier) {
        colHeaders.add("Validation Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if(modelCategory == ModelCategory.AutoEncoder) {
        colHeaders.add("Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      }
    } // (hasValidation)
    if (hasCrossValidation) {
      colHeaders.add("Cross-Validation RMSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (modelCategory == ModelCategory.Regression) {
        colHeaders.add("Cross-Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Cross-Validation MAE"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (isClassifier) {
        colHeaders.add("Cross-Validation LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (modelCategory == ModelCategory.Binomial) {
        colHeaders.add("Cross-Validation AUC"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (modelCategory == ModelCategory.Binomial) {
        colHeaders.add("Cross-Validation Lift"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (isClassifier) {
        colHeaders.add("Cross-Validation Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if(modelCategory == ModelCategory.AutoEncoder) {
        colHeaders.add("Cross-Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      }
    } // (hasCrossValidation)


    final int rows = scoringInfos == null ? 0 : scoringInfos.length;
    String[] s = new String[0];
    TwoDimTable table = new TwoDimTable(
      "Scoring History", null,
      new String[rows],
      colHeaders.toArray(s),
      colTypes.toArray(s),
      colFormat.toArray(s),
      "");
    int row = 0;

    if (null == scoringInfos)
      return table;

    for (ScoringInfo si : scoringInfos) {
      int col = 0;
      assert (row < table.getRowDim());
      assert (col < table.getColDim());
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(si.time_stamp_ms));
      table.set(row, col++, PrettyPrint.msecs(si.total_training_time_ms, true));

      if (hasSamples) {
//      Log.info("1st speed: (samples: " + si.training_samples + ", total_run_time: " + si.total_training_time_ms + ", total_scoring_time: " + si.total_scoring_time_ms + ", total_setup_time: " + si.total_setup_time_ms + ")");
        float speed = (float) (((HasSamples)si).training_samples() / ((1.+si.total_training_time_ms - si.total_scoring_time_ms - si.total_setup_time_ms) / 1e3));
        assert (speed >= 0) : "Speed should not be negative! " + speed + " = (float)(" + ((HasSamples)si).training_samples() + "/((" + si.total_training_time_ms + "-" + si.total_scoring_time_ms + "-" + si.total_setup_time_ms + ")/1e3)";
        table.set(row, col++, si.total_training_time_ms == 0 ? null : (
                speed>10 ? String.format("%d", (int)speed) : String.format("%g", speed)
        ) + " obs/sec");
      }
      if (hasEpochs) table.set(row, col++, ((HasEpochs)si).epoch_counter());
      if (hasIterations) table.set(row, col++, ((HasIterations)si).iterations());
      if (hasSamples) table.set(row, col++, ((HasSamples)si).training_samples());

      table.set(row, col++, si.scored_train != null ? si.scored_train._rmse : Double.NaN);
      if (modelCategory == ModelCategory.Regression) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._mean_residual_deviance : Double.NaN);
      }
      if (modelCategory == ModelCategory.Regression) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._mae : Double.NaN);
      }
      if (isClassifier) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._logloss : Double.NaN);
      }
      if (modelCategory == ModelCategory.Binomial) {
        table.set(row, col++, si.training_AUC != null ? si.training_AUC._auc : Double.NaN);
        table.set(row, col++, si.scored_train != null ? si.scored_train._lift : Double.NaN);
      }
      if (isClassifier) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._classError : Double.NaN);
      }
      if (isAutoencoder) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._mse : Double.NaN);
      }
      if (hasValidation) {
        table.set(row, col++, si.scored_valid != null ? si.scored_valid._rmse : Double.NaN);
        if (modelCategory == ModelCategory.Regression) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._mean_residual_deviance : Double.NaN);
        }
        if (modelCategory == ModelCategory.Regression) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._mae : Double.NaN);
        }
        if (isClassifier) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._logloss : Double.NaN);
        }
        if (modelCategory == ModelCategory.Binomial) {
          table.set(row, col++, si.validation_AUC != null ? si.validation_AUC._auc : Double.NaN);
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._lift : Double.NaN);
        }
        if (isClassifier) {
          table.set(row, col, si.scored_valid != null ? si.scored_valid._classError : Double.NaN);
        }
        if (isAutoencoder) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._mse : Double.NaN);
        }
      } // hasValidation
      if (hasCrossValidation) {
        table.set(row, col++, si.scored_xval != null ? si.scored_xval._rmse : Double.NaN);
        if (modelCategory == ModelCategory.Regression) {
          table.set(row, col++, si.scored_xval != null ? si.scored_xval._mean_residual_deviance : Double.NaN);
        }
        if (modelCategory == ModelCategory.Regression) {
          table.set(row, col++, si.scored_xval != null ? si.scored_xval._mae : Double.NaN);
        }
        if (isClassifier) {
          table.set(row, col++, si.scored_xval != null ? si.scored_xval._logloss : Double.NaN);
        }
        if (modelCategory == ModelCategory.Binomial) {
          table.set(row, col++, si.validation_AUC != null ? si.validation_AUC._auc : Double.NaN);
          table.set(row, col++, si.scored_xval != null ? si.scored_xval._lift : Double.NaN);
        }
        if (isClassifier) {
          table.set(row, col, si.scored_xval != null ? si.scored_xval._classError : Double.NaN);
        }
        if (isAutoencoder) {
          table.set(row, col++, si.scored_xval != null ? si.scored_xval._mse : Double.NaN);
        }
      } // hasCrossValidation
      row++;
    }
    return table;
  }
}
