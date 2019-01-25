package water.persist;

import water.*;
import water.util.IcedBitSet;

public class S3CredentialsMRTask extends MRTask<S3CredentialsMRTask> {
    
    private final String _secretKeyId;
    private final String _secretAccessKey;

    public S3CredentialsMRTask(final String secretKeyId, final String secretAccessKey) {
        _secretKeyId = secretKeyId;
        _secretAccessKey = secretAccessKey;
    }

    @Override
    protected void setupLocal() {
        PersistS3.setAmazonS3Credentials(_secretKeyId, _secretAccessKey);
    }
}
