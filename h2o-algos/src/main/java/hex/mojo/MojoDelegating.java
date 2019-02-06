package hex.mojo;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;

import java.io.IOException;
import java.io.InputStream;

public class MojoDelegating extends ModelBuilder<MojoDelegatingModel, MojoDelegatingModelParameters, MojoDelegatingModelOutput> {

    public MojoDelegating(MojoDelegatingModelParameters parms) {
        super(parms);
    }

    public MojoDelegating(boolean startup_once) {
        super(new MojoDelegatingModelParameters(), startup_once);
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
                final MojoModel mojoModel = MojoModel.load(MojoReaderBackendFactory.createReaderBackend(_parms._mojoData.openStream(_job._key), MojoReaderBackendFactory.CachingStrategy.MEMORY));
                mojoDelegatingModel._mojoModel = mojoModel;
            } catch (IOException e) {
                throw new IllegalStateException("Unreachable MOJO file: " + _parms._mojoData._key, e);
            }

            mojoDelegatingModel.unlock(_job);
        }
    }
}
