package water.persist;

import water.Iced;
import water.api.API;
import water.api.schemas3.SchemaV3;

public class PersistS3ACredentialsV3 extends SchemaV3<Iced, PersistS3ACredentialsV3> {

  /**
   * Value for fs.s3.awsAccessKeyId configuration value
     */
    @API(required = true, direction = API.Direction.INPUT, level = API.Level.secondary, help = "S3 Secret Key ID")
    public String access_key_id;

  /**
   * Value for fs.s3.awsSecretAccessKey configuration value
     */
    @API(required = true, direction = API.Direction.INPUT, level = API.Level.secondary, help = "S3 Secret Key")
    public  String secret_access_key;
}
