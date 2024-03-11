package ai.h2o.automl.dummy;

import ai.h2o.automl.IAlgo;
import hex.ModelBuilder;
import hex.ModelCategory;
import org.junit.Ignore;
import water.exceptions.H2OIllegalArgumentException;

@Ignore("utility class")
public class DummyBuilder extends ModelBuilder<DummyModel, DummyModel.DummyModelParameters, DummyModel.DummyModelOutput> {

    public static IAlgo algo = new IAlgo() {
        @Override
        public String name() {
            return "dummy";
        }
    };

    public DummyBuilder(boolean startup_once) {
        super(new DummyModel.DummyModelParameters(), startup_once);
    }

    public DummyBuilder(DummyModel.DummyModelParameters parms) {
        super(parms);
        init(false);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms._fail_on_init) {
            throw new H2OIllegalArgumentException("Failing on request");
        }
    }
    
    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[] {ModelCategory.Binomial};
    }

    @Override
    public boolean isSupervised() {
        return true;
    }

    @Override
    public String getName() {
        return algo.urlName();
    }

    @Override
    protected Driver trainModelImpl() {
        return new DummyDriver();
    }

    class DummyDriver extends Driver {
        @Override
        public void computeImpl() {
            init(true);
            DummyModel model = new DummyModel(_result, _parms, new DummyModel.DummyModelOutput(DummyBuilder.this));
            model.delete_and_lock(_job);
            model.update(_job);
            model.unlock(_job);
            _job.update(_job._work);
        }
    }
}
