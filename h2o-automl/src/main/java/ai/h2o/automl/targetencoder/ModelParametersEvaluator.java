package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import hex.Model;
import hex.ModelBuilder;
import water.Iced;
import water.fvec.Frame;

public abstract class ModelParametersEvaluator<M extends Model, MP extends Model.Parameters> extends Iced {

  public abstract ModelParametersSelectionStrategy.Evaluated<M> evaluate(MP modelParameters,
                                                                      ModelBuilder modelBuilder,
                                                                      ModelValidationMode modelValidationMode,
                                                                      Frame leaderboard,
                                                                      String[] columnNamesToEncode,
                                                                      long seedForFoldColumn);
}
