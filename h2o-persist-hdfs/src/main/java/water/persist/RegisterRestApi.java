package water.persist;

import water.api.AbstractRegister;
import water.api.RestApiContext;

public class RegisterRestApi extends AbstractRegister {
    @Override
    public void registerEndPoints(RestApiContext context) {
        context.registerEndpoint("set_s3a_credentials", "POST /3/PersistS3A", PersistS3AHandler.class, "setS3ACredentials", "Set AWS credentials for the S3A protocol (Secret Key ID, Secret Access Key)");
    }

    @Override
    public String getName() {
        return "Amazon S3A";
    }
}
