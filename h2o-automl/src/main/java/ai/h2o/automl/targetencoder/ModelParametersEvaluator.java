package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import hex.Model;
import hex.ModelBuilder;
import water.Iced;
import water.fvec.Frame;

public abstract class ModelParametersEvaluator<MP extends Model.Parameters> extends Iced {

  public abstract double evaluate(MP modelParameters,
                         ModelBuilder modelBuilder,
                         ModelValidationMode modelValidationMode,
                         Frame leaderboard,
                         String[] columnNamesToEncode,
                         long seedForFoldColumn);
}
