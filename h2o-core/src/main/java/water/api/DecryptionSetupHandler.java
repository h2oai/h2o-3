package water.api;

import water.api.schemas3.DecryptionSetupV3;
import water.parser.DecryptionTool;

public class DecryptionSetupHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DecryptionSetupV3 setupDecryption(int version, DecryptionSetupV3 dsV3) {
    DecryptionTool.DecryptionSetup ds = dsV3.fillImpl(new DecryptionTool.DecryptionSetup());
    DecryptionTool tool = DecryptionTool.make(ds);
    ds._decrypt_tool_id = tool._key;
    return new DecryptionSetupV3().fillFromImpl(ds);
  }

}
