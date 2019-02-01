package hex.mojo;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.MojoModel;
import water.DKV;
import water.Value;
import water.fvec.ByteVec;
import water.fvec.Frame;

import java.io.IOException;

public class MojoDelegating extends ModelBuilder<MojoDelegatingModel, MojoDelegatingModelParameters, MojoDelegatingModelOutput> {

    public MojoDelegating(MojoDelegatingModelParameters parms) {
        super(parms);
    }

    public MojoDelegating(boolean startup_once) {
        super(new MojoDelegatingModelParameters(), true);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
    }

    @Override
    protected Driver trainModelImpl() {
        return new MojoDelegatingModelDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return ModelCategory.values();
    }

    @Override
    public boolean isSupervised() {
        // TODO: should return value based on underlying MOJ
        return false;
    }

    class MojoDelegatingModelDriver extends Driver {

        @Override
        public void computeImpl() {
            final MojoDelegatingModelOutput mojoDelegatingModelOutput = new MojoDelegatingModelOutput();
            final MojoDelegatingModel mojoDelegatingModel = new MojoDelegatingModel(_result, _parms, mojoDelegatingModelOutput);
            mojoDelegatingModel.write_lock(_job);

            try {
                final MojoModel mojoModel = MojoModel.load(_parms.mojoFile);
                mojoDelegatingModel._mojoModel = mojoModel;
            } catch (IOException e) {
                throw new IllegalStateException("Unreachable MOJO file: " + _parms.mojoFile, e);
            }

            mojoDelegatingModel.unlock(_job);
        }
    }
}
