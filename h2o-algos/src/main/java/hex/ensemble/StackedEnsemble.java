package hex.ensemble;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.StackedEnsembleModel;
import water.exceptions.H2OModelBuilderIllegalArgumentException;

/**
 * An ensemble of other models, created by <i>stacking</i> with the SuperLearner algorithm or a variation.
 */
public class StackedEnsemble extends ModelBuilder<StackedEnsembleModel,StackedEnsembleModel.StackedEnsembleParameters,StackedEnsembleModel.StackedEnsembleOutput> {
  StackedEnsembleDriver _driver;
  // The in-progress model being built
  protected StackedEnsembleModel _model;



  public StackedEnsemble(boolean startup_once) { super(new StackedEnsembleModel.StackedEnsembleParameters(),startup_once); }

  /*
  public StackedEnsemble(Key selfKey, StackedEnsembleModel.StackedEnsembleParameters parms, StackedEnsemble job) {
    super(selfKey, parms, job == null ?
            new StackedEnsembleModel.StackedEnsembleOutput():
            new StackedEnsembleModel.StackedEnsembleOutput(job));
  }
  */

  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
         //   ModelCategory.Multinomial, // TODO
    };
  }

  @Override protected StackedEnsembleDriver trainModelImpl() { return _driver = new StackedEnsembleDriver(); }

  private class StackedEnsembleDriver extends Driver {

    public void computeImpl() {
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(StackedEnsemble.this);

      _model = new StackedEnsembleModel(dest(), _parms);
      _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)

      // TODO: stuff
      _model.checkAndInheritModelProperties();

      // finally:
      _model.update(_job);

    }
  }


}
