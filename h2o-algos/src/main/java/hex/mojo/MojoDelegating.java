package hex.mojo;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackendFactory;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;

import java.io.IOException;
import java.util.Objects;

public class MojoDelegating extends ModelBuilder<MojoDelegatingModel, MojoDelegatingModelParameters, MojoDelegatingModelOutput> {

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
            final ByteVec mojoData = getUploadedMojo(_parms._mojo_key);
            final MojoModel mojoModel;
            try {
                mojoModel = MojoModel.load(MojoReaderBackendFactory.createReaderBackend(mojoData.openStream(_job._key), MojoReaderBackendFactory.CachingStrategy.MEMORY));
            } catch (IOException e) {
                throw new IllegalStateException("Unreachable MOJO file: " + mojoData._key, e);
            }
            final MojoDelegatingModelOutput mojoDelegatingModelOutput = new MojoDelegatingModelOutput(mojoModel);
            final MojoDelegatingModel mojoDelegatingModel = new MojoDelegatingModel(_result, _parms, mojoDelegatingModelOutput, mojoModel);
            mojoDelegatingModel.write_lock(_job);
            mojoDelegatingModel.unlock(_job);
        }
    }
    


    /**
     * Retrieves pre-uploaded MOJO archive and performs basic verifications, if present.
     *
     * @param key Key to MOJO bytes in DKV
     * @return An instance of {@link ByteVec} containing the bytes of an uploaded MOJO, if present. Or exception. Never returns null.
     * @throws IllegalArgumentException In case the supplied key is invalid (MOJO missing, empty key etc.)
     */
    private final ByteVec getUploadedMojo(final Key<Frame> key) throws IllegalArgumentException {
        Objects.requireNonNull(key); // Nicer null pointer exception in case null key is accidentally provided

        Frame mojoFrame = key.get();
        if (mojoFrame.numCols() > 1)
            throw new IllegalArgumentException(String.format("Given MOJO frame with key '%s' should contain only 1 column with MOJO bytes. More columns found. Incorrect key provided ?", key));
        ByteVec mojoData = (ByteVec) mojoFrame.anyVec();

        if (mojoData.length() < 1)
            throw new IllegalArgumentException(String.format("Given MOJO frame with key '%s' is empty (0 bytes). Please provide a non-empty MOJO file.", key));

        return mojoData;
    }
}
