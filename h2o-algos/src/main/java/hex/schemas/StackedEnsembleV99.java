package hex.schemas;

import hex.ensemble.StackedEnsemble;
import hex.StackedEnsembleModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

/**
 * Created by rpeck on 10/11/16.
 */
public class StackedEnsembleV99 extends ModelBuilderSchema<StackedEnsemble,StackedEnsembleV99,StackedEnsembleV99.StackedEnsembleParametersV99> {
  public static final class StackedEnsembleParametersV99 extends ModelParametersSchemaV3<StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleParametersV99> {
    static public String[] fields = new String[]{
            "selection_strategy",
            "base_models",
    };

    @API(help = "Strategy for choosing which models to stack.", values = { "choose_all" }, gridable = false)
    public StackedEnsembleModel.StackedEnsembleParameters.SelectionStrategy selection_strategy;

    @API(help = "List of models which we can stack together.  Which ones are chosen depends on the selection_strategy.", required = true)
    public KeyV3.ModelKeyV3 base_models[];
  }
}
