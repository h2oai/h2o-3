package water.test.dummy;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.Job;

public class DummyModelBuilder
    extends ModelBuilder<DummyModel, DummyModelParameters, DummyModelOutput> {
  public DummyModelBuilder(DummyModelParameters parms) {
    super(parms);
    init(false);
  }

  public DummyModelBuilder(DummyModelParameters parms, boolean startup_once ) { super(parms,startup_once); }

  @Override
  protected Driver trainModelImpl() {
    return new Driver() {
      @Override
      public void computeImpl() {
        if (_parms._cancel_job)
          throw new Job.JobCancelledException();
        String msg = null;
        if (_parms._action != null) 
          msg = _parms._action.run(_parms);
        if (! _parms._makeModel)
          return;
        init(true);
        Model model = null;
        try {
          model = new DummyModel(dest(), _parms, new DummyModelOutput(DummyModelBuilder.this, train(), msg));
          model.delete_and_lock(_job);
          model.update(_job);
        } finally {
          if (model != null)
            model.unlock(_job);
        }
      }
    };
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[0];
  }

  @Override
  public boolean isSupervised() {
    return true;
  }
}
