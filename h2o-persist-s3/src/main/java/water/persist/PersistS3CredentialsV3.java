package water.persist;

import water.Iced;
import water.api.API;
import water.api.schemas3.SchemaV3;

public class PersistS3CredentialsV3 extends SchemaV3<Iced, PersistS3CredentialsV3> {
    
    @API(required = true, direction = API.Direction.INPUT, level = API.Level.secondary, help = "S3 Secret Key ID")
    public String secret_key_id;
    
    @API(required = true, direction = API.Direction.INPUT, level = API.Level.secondary, help = "S3 Secret Key")
    public  String secret_access_key;
    
    @API(required = false, direction = API.Direction.INPUT, level = API.Level.secondary, help = "S3 Session token")
    public String session_token;
}
