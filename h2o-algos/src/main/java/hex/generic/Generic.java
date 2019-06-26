package hex.generic;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.*;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.glm.GlmMojoModel;
import hex.genmodel.algos.isofor.IsolationForestMojoModel;
import hex.genmodel.algos.kmeans.KMeansMojoModel;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * Generic model able to do scoring with any underlying model deserializable into a format known by the {@link GenericModel}.
 * Only H2O Mojos are currently supported.
 */
public class Generic extends ModelBuilder<GenericModel, GenericModelParameters, GenericModelOutput> {
    
    private static final Class[] SUPPORTED_MOJOS = new Class[]{GlmMojoModel.class, GbmMojoModel.class,
            IsolationForestMojoModel.class, DrfMojoModel.class, KMeansMojoModel.class};

    public Generic(GenericModelParameters genericParameters){
        super(genericParameters);
        init(false);
    }

    public Generic(boolean startup_once) {
        super(new GenericModelParameters(), startup_once);
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
    public boolean haveMojo() {
        return true;
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    class MojoDelegatingModelDriver extends Driver {
        @Override
        public void computeImpl() {
            final ByteVec mojoBytes = getUploadedMojo(_parms._model_key);
            final MojoModel mojoModel;
            try {
                final MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(mojoBytes.openStream(_job._key), MojoReaderBackendFactory.CachingStrategy.MEMORY);
                mojoModel = ModelMojoReader.readFrom(readerBackend, true);
                
                if(!ArrayUtils.isInstance(mojoModel, SUPPORTED_MOJOS)){
                    throw new IllegalArgumentException(String.format("Unsupported MOJO model %s. ", mojoModel.getClass().getName()));
                }

                final GenericModelOutput genericModelOutput = new GenericModelOutput(mojoModel._modelDescriptor, mojoModel._modelAttributes);
                final GenericModel genericModel = new GenericModel(_result, _parms, genericModelOutput, mojoModel, mojoBytes);

                genericModel.write_lock(_job);
                genericModel.unlock(_job);
            } catch (IOException e) {
                throw new IllegalStateException("Unreachable MOJO file: " + mojoBytes._key, e);
            }
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

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Stable;
    }
}
