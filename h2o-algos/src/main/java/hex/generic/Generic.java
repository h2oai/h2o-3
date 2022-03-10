package hex.generic;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.genmodel.*;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.descriptor.ModelDescriptorBuilder;
import water.H2O;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.parser.ZipUtil;
import water.util.Log;

import java.io.IOException;
import java.util.*;

/**
 * Generic model able to do scoring with any underlying model deserializable into a format known by the {@link GenericModel}.
 * Only H2O Mojos are currently supported.
 */
public class Generic extends ModelBuilder<GenericModel, GenericModelParameters, GenericModelOutput> {

    /**
     * Unmodifiable {@link Set} of Algorithm MOJOs which are allowed to be imported as generic model
     */
    private static final Set<String> ALLOWED_MOJO_ALGOS;
    static{ 
        final Set<String> allowedAlgos = new HashSet<>(6);
        allowedAlgos.add("gbm");
        allowedAlgos.add("glm");
        allowedAlgos.add("xgboost");
        allowedAlgos.add("isolationforest");
        allowedAlgos.add("extendedisolationforest");
        allowedAlgos.add("drf");
        allowedAlgos.add("deeplearning");
        allowedAlgos.add("stackedensemble");
        allowedAlgos.add("coxph");
        allowedAlgos.add("rulefit");
        allowedAlgos.add("gam");
        
        ALLOWED_MOJO_ALGOS = Collections.unmodifiableSet(allowedAlgos);
    }


    public Generic(GenericModelParameters genericParameters){
        super(genericParameters);
        init(false);
    }

    public Generic(boolean startup_once) {
        super(new GenericModelParameters(), startup_once);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms._path != null && _parms._model_key != null) {
            error("_path", 
                    "Path cannot be set for MOJO that is supposed to be loaded from distributed memory (key=" + _parms._model_key + ").");
        }
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
        public void compute2() {
            if (_parms._path != null) { // If there is a file to be imported, do the import before the scope is entered
                _parms._model_key = importFile();
            }
            super.compute2();
        }

        @Override
        public void computeImpl() {
            final Key<Frame> dataKey;
            if (_parms._model_key != null) {
                dataKey = _parms._model_key;
            } else {
                throw new IllegalArgumentException("Either MOJO zip path or key to the uploaded MOJO frame must be specified");
            }
            final ByteVec modelBytes = readModelData(dataKey);
            try {
                final GenericModel genericModel;
                if (ZipUtil.isCompressed(modelBytes)) {
                    genericModel = importMojo(modelBytes, dataKey);
                } else {
                    warn("_path", "Trying to import a POJO model - this is currently an experimental feature.");
                    genericModel = importPojo(modelBytes, dataKey);
                }
                genericModel.write_lock(_job);
                genericModel.unlock(_job);
            } catch (IOException e) {
                throw new IllegalStateException("Unreachable model file: " + dataKey, e);
            }
        }

        private GenericModel importMojo(ByteVec mojoBytes, Key<Frame> dataKey) throws IOException {
            final MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(
                    mojoBytes.openStream(_job._key), MojoReaderBackendFactory.CachingStrategy.MEMORY);
            final MojoModel mojoModel = ModelMojoReader.readFrom(readerBackend, true);

            if(! ALLOWED_MOJO_ALGOS.contains(mojoModel._modelDescriptor.algoName().toLowerCase())) {
                if (_parms._disable_algo_check)
                    Log.warn(String.format("MOJO model '%s' is not supported but user disabled white-list check. Trying to load anyway.", mojoModel._modelDescriptor.algoName()));
                else
                    throw new IllegalArgumentException(String.format("Unsupported MOJO model '%s'. ", mojoModel._modelDescriptor.algoName()));
            }

            final GenericModelOutput genericModelOutput = new GenericModelOutput(mojoModel._modelDescriptor, mojoModel._modelAttributes, mojoModel._reproducibilityInformation);
            return new GenericModel(_result, _parms, genericModelOutput, mojoModel, dataKey);
        }

        private GenericModel importPojo(ByteVec pojoBytes, Key<Frame> pojoKey) throws IOException {
            GenModel genmodel = PojoLoader.loadPojoFromSourceCode(pojoBytes, pojoKey);
            ModelDescriptor pojoDescriptor = ModelDescriptorBuilder.makeDescriptor(genmodel);
            final GenericModelOutput genericModelOutput = new GenericModelOutput(pojoDescriptor);
            return new GenericModel(_result, _parms, genericModelOutput, genmodel, pojoKey);
        }
    }

    private Key<Frame> importFile() {
        ArrayList<String> files = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> fails = new ArrayList<>();
        ArrayList<String> dels = new ArrayList<>();
        H2O.getPM().importFiles(_parms._path, null, files, keys, fails, dels);
        if (!fails.isEmpty()) {
            throw new RuntimeException("Failed to import file: " + Arrays.toString(fails.toArray()));
        }
        assert keys.size() == 1;
        return Key.make(keys.get(0));
    }

    /**
     * Retrieves pre-uploaded MOJO archive and performs basic verifications, if present.
     *
     * @param key Key to MOJO bytes in DKV
     * @return An instance of {@link ByteVec} containing the bytes of an uploaded MOJO, if present. Or exception. Never returns null.
     * @throws IllegalArgumentException In case the supplied key is invalid (MOJO missing, empty key etc.)
     */
    private ByteVec readModelData(final Key<Frame> key) throws IllegalArgumentException {
        Objects.requireNonNull(key); // Nicer null pointer exception in case null key is accidentally provided

        Frame mojoFrame = key.get();
        if (mojoFrame.numCols() > 1)
            throw new IllegalArgumentException(String.format("Given model frame with key '%s' should contain only 1 column with model bytes. More columns found. Incorrect key provided ?", key));
        ByteVec mojoData = (ByteVec) mojoFrame.anyVec();

        if (mojoData.length() < 1)
            throw new IllegalArgumentException(String.format("Given model frame with key '%s' is empty (0 bytes). Please provide a non-empty model file.", key));

        return mojoData;
    }

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Stable;
    }

    /**
     * Convenience method for importing MOJO into H2O.
     * 
     * @param location absolute path to MOJO file
     * @param disableAlgoCheck if true skip the check of white-listed MOJO models, use at your own risk - some features might not work.
     * @return instance of H2O Model wrapping a MOJO 
     */
    public static GenericModel importMojoModel(String location, boolean disableAlgoCheck) {
        GenericModelParameters p = new GenericModelParameters();
        p._path = location;
        p._disable_algo_check = disableAlgoCheck;
        return new Generic(p).trainModel().get();
    }

}
