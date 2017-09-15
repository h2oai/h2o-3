package water.api.schemas3;

import water.api.API;
import water.parser.*;

public class DecryptionSetupV3 extends RequestSchemaV3<DecryptionTool.DecryptionSetup, DecryptionSetupV3> {

  @API(help = "Target key for the Decryption Tool", direction = API.Direction.INOUT)
  public KeyV3.DecryptionToolKeyV3 decrypt_tool_id;

  @API(help = "Implementation of the Decryption Tool", direction = API.Direction.INOUT)
  public String decrypt_impl;

  @API(help = "Location of Java Keystore", direction = API.Direction.INOUT)
  public KeyV3.FrameKeyV3 keystore_id;

  @API(help = "Keystore type", direction = API.Direction.INOUT)
  public String keystore_type;

  @API(help = "Key alias", direction = API.Direction.INOUT)
  public String key_alias;

  @API(help = "Key password", direction = API.Direction.INPUT)
  public String password;

  @API(help = "Specification of the cipher (and padding)", direction = API.Direction.INOUT)
  public String cipher_spec;

  @Override
  public DecryptionTool.DecryptionSetup fillImpl(DecryptionTool.DecryptionSetup impl) {
    DecryptionTool.DecryptionSetup ds = fillImpl(impl, new String[]{"password"});
    ds._password = this.password.toCharArray();
    return ds;
  }

  @Override
  public DecryptionSetupV3 fillFromImpl(DecryptionTool.DecryptionSetup impl) {
    return fillFromImpl(impl, new String[] {"password"});
  }

}
