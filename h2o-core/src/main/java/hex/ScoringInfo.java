package hex;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.AutoBuffer;
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
public class ScoringInfo extends Iced {
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
  public VarImp variable_importances;
  public ScoreKeeper scored_train = new ScoreKeeper();
  public ScoreKeeper scored_valid = new ScoreKeeper();

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

  /** For a given array of ScoringInfo return an array of the validation or training ScoreKeepers, as available. */
  public static ScoreKeeper[] scoreKeepers(ScoringInfo[] scoring_history) {
    ScoreKeeper[] sk = new ScoreKeeper[scoring_history.length];
    for (int i=0;i<sk.length;++i) {
      // TODO: don't allow mixing of training and validation metrics
      sk[i] = scoring_history[i].validation ? scoring_history[i].scored_valid : scoring_history[i].scored_train;
    }
    return sk;
  }

  public double metric(ScoreKeeper.StoppingMetric criterion) {
      switch (criterion) {
        case AUC:               { return validation ? scored_valid._AUC : scored_train._AUC; }
        case MSE:               { return validation ? scored_valid._mse : scored_train._mse; }
        case deviance:          { return validation ? scored_valid._mean_residual_deviance : scored_train._mean_residual_deviance; }
        case logloss:           { return validation ? scored_valid._logloss : scored_train._logloss; }
        case r2:                { return validation ? scored_valid._r2 : scored_train._r2; }
        case misclassification: { return validation ? scored_valid._classError : scored_train._classError; }
        case lift_top_group:    { return validation ? scored_valid._lift : scored_train._lift; }
        default:                throw H2O.unimpl("Undefined stopping criterion: " + criterion);
      }
  }

  /**
   * Create a java.util.Comparator which allows us to sort an array of ScoringInfo based on a stopping criterion / metric.
   * Uses validation metrics if available, otherwise falls back to training metrics.  Understands whether more is better
   * for the given criterion and will order the array so that the best models are first.
   * @param criterion scalar model metric / stopping criterion by which to sort
   * @return a Comparator on a stopping criterion / metric
   */
  public static final Comparator<ScoringInfo> comparator(final ScoreKeeper.StoppingMetric criterion) {
    return new Comparator<ScoringInfo>() {
      @Override
      public int compare(ScoringInfo o1, ScoringInfo o2) {
        boolean moreIsBetter = ScoreKeeper.moreIsBetter(criterion);

        if (moreIsBetter)
          return (int)Math.signum(o2.metric(criterion) - o1.metric(criterion)); // moreIsBetter
        else
          return (int)Math.signum(o1.metric(criterion) - o2.metric(criterion)); // lessIsBetter
      }
    };
  }

  /**
   * Sort an array of ScoringInfo based on a stopping criterion / metric.
   * Uses validation metrics if available, otherwise falls back to training metrics.  Understands whether more is better
   * for the given criterion and will order the array so that the best models are first.
   * @param scoringInfos array of ScoringInfo to sort
   * @param criterion scalar model metric / stopping criterion by which to sort
   */
  public static void sort(ScoringInfo[] scoringInfos, ScoreKeeper.StoppingMetric criterion) {
    if (null == scoringInfos) return;
    if (scoringInfos.length == 0) return;

    // handle StoppingMetric.AUTO
    if (criterion == ScoreKeeper.StoppingMetric.AUTO)
      criterion = scoringInfos[0].is_classification ? ScoreKeeper.StoppingMetric.logloss : scoringInfos[0].is_autoencoder ? ScoreKeeper.StoppingMetric.MSE : ScoreKeeper.StoppingMetric.deviance;

    Arrays.sort(scoringInfos, ScoringInfo.comparator(criterion));
  }

  /** Based on the given array of ScoringInfo and stopping criteria should we stop early? */
  public static boolean stopEarly(ScoringInfo[] sk, int k, boolean classification, ScoreKeeper.StoppingMetric criterion, double rel_improvement) {
    return ScoreKeeper.stopEarly(ScoringInfo.scoreKeepers(sk), k, classification, criterion, rel_improvement);
  }

  /**
   * Create a TwoDimTable to display the scoring history from an array of scoringInfo.
   * @param scoringInfos array of ScoringInfo to render
   * @param hasValidation do we have validation metrics?
   * @param modelCategory the category for the model or models
   * @param isAutoencoder is the model or are the models autoencoders?
   * @return
   */
  public static TwoDimTable createScoringHistoryTable(ScoringInfo[] scoringInfos, boolean hasValidation, ModelCategory modelCategory, boolean isAutoencoder) {
    boolean hasEpochs = (scoringInfos instanceof HasEpochs[]);
    boolean hasSamples = (scoringInfos instanceof HasSamples[]);
    boolean hasIterations = (scoringInfos instanceof HasIterations[]);
    boolean isClassifier = (modelCategory == ModelCategory.Binomial || modelCategory == ModelCategory.Multinomial);

    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    if (hasSamples) { colHeaders.add("Training Speed"); colTypes.add("string"); colFormat.add("%s"); }
    if (hasEpochs) { colHeaders.add("Epochs"); colTypes.add("double"); colFormat.add("%.5f"); }
    if (hasIterations) { colHeaders.add("Iterations"); colTypes.add("int"); colFormat.add("%d"); }
    if (hasSamples) { colHeaders.add("Samples"); colTypes.add("double"); colFormat.add("%f"); }
    colHeaders.add("Training MSE"); colTypes.add("double"); colFormat.add("%.5f");

    if (modelCategory == ModelCategory.Regression) {
      colHeaders.add("Training Deviance"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (!isAutoencoder) {
      colHeaders.add("Training R^2"); colTypes.add("double"); colFormat.add("%.5f");
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
    if (hasValidation) {
      colHeaders.add("Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (modelCategory == ModelCategory.Regression) {
        colHeaders.add("Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (!isAutoencoder) {
        colHeaders.add("Validation R^2"); colTypes.add("double"); colFormat.add("%.5f");
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
    } // (hasValidation)


    final int rows = scoringInfos.length;
    String[] s = new String[0];
    TwoDimTable table = new TwoDimTable(
      "Scoring History", null,
      new String[rows],
      colHeaders.toArray(s),
      colTypes.toArray(s),
      colFormat.toArray(s),
      "");
    int row = 0;
    for (ScoringInfo si : scoringInfos) {
      int col = 0;
      assert (row < table.getRowDim());
      assert (col < table.getColDim());
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(si.time_stamp_ms));
      table.set(row, col++, PrettyPrint.msecs(si.total_training_time_ms, true));

      if (hasSamples) {
//      Log.info("1st speed: (samples: " + si.training_samples + ", total_run_time: " + si.total_training_time_ms + ", total_scoring_time: " + si.total_scoring_time_ms + ", total_setup_time: " + si.total_setup_time_ms + ")");
        int speed = (int) (((HasSamples)si).training_samples() / ((si.total_training_time_ms - si.total_scoring_time_ms - si.total_setup_time_ms) / 1e3));
        assert (speed >= 0) : "Speed should not be negative! " + speed + " = (int)(" + ((HasSamples)si).training_samples() + "/((" + si.total_training_time_ms + "-" + si.total_scoring_time_ms + "-" + si.total_setup_time_ms + ")/1e3)";
        table.set(row, col++, si.total_training_time_ms == 0 ? null : (String.format("%d", speed) + " rows/sec"));
      }
      if (hasEpochs) table.set(row, col++, ((HasEpochs)si).epoch_counter());
      if (hasIterations) table.set(row, col++, ((HasIterations)si).iterations());
      if (hasSamples) table.set(row, col++, ((HasSamples)si).training_samples());

      table.set(row, col++, si.scored_train != null ? si.scored_train._mse : Double.NaN);
      if (modelCategory == ModelCategory.Regression) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._mean_residual_deviance : Double.NaN);
      }
      if (!isAutoencoder) {
        table.set(row, col++, si.scored_train != null ? si.scored_train._r2 : Double.NaN);
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
      if (hasValidation) {
        table.set(row, col++, si.scored_valid != null ? si.scored_valid._mse : Double.NaN);
        if (modelCategory == ModelCategory.Regression) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._mean_residual_deviance : Double.NaN);
        }
        if (!isAutoencoder) {
          table.set(row, col++, si.scored_valid != null ? si.scored_valid._r2 : Double.NaN);
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
      } // hasValidation
      row++;
    }
    return table;
  }

  public ScoringInfo deep_clone() {
    AutoBuffer ab = new AutoBuffer();
    this.write(ab);
    ab.flipForReading();
    return (ScoringInfo) new ScoringInfo().read(ab);
  }
}
