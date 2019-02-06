package hex.mojo.api;

import hex.mojo.MojoDelegating;
import hex.mojo.MojoDelegatingModel;
import hex.mojo.MojoDelegatingModelParameters;
import water.DKV;
import water.Job;
import water.Value;
import water.api.Handler;
import water.fvec.ByteVec;
import water.fvec.Frame;

public class MojoDelegatingModelHandler extends Handler {

    public MojoDelegatingModelV3 createMojoDelegatingModel(int version, MojoDelegatingModelV3 mojoDelegatingModelV3) {

        final ByteVec mojoData = getUploadedMojo(mojoDelegatingModelV3.mojo_file_key);

        final MojoDelegatingModelParameters parameters = new MojoDelegatingModelParameters();
        parameters._mojoData = mojoData;

        final MojoDelegating mojoDelegating = new MojoDelegating(parameters);
        mojoDelegating.init(false);
        final Job<MojoDelegatingModel> mojoDelegatingModelJob = mojoDelegating.trainModel();

        return mojoDelegatingModelV3;
    }

    /**
     * Retrieves pre-uploaded MOJO archive and performs basic verifications, if present.
     *
     * @param key Key to MOJO bytes in DKV
     * @return An instance of {@link ByteVec} containing the bytes of an uploaded MOJO, if present. Or exception. Never returns null.
     * @throws IllegalArgumentException In case the supplied key is invalid (MOJO missing, empty key etc.)
     */
    private final ByteVec getUploadedMojo(final String key) throws IllegalArgumentException {
        final Value value = DKV.get(key);
        if (value == null)
            throw new IllegalArgumentException(String.format("Given MOJO file key '%s' is not to be found in H2O.", value));
        if (!value.isFrame())
            throw new IllegalArgumentException(String.format("Given MOJO file key should a one-columnar Frame, however the following type has been found: '%d'", value.type()));

        Frame mojoFrame = value.get();
        if (mojoFrame.numCols() > 1)
            throw new IllegalArgumentException(String.format("Given MOJO frame with key '%s' should contain only 1 column with MOJO bytes. More columns found. Incorrect key provided ?", key));
        ByteVec mojoData = (ByteVec) mojoFrame.anyVec();

        if (mojoData.length() < 1)
            throw new IllegalArgumentException(String.format("Given MOJO frame with key '%s' is empty (0 bytes). Please provide a non-empty MOJO file.", key));

        return mojoData;
    }

}
