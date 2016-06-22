package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 * Model import REST end-point.
 */
public class ModelImportV3 extends SchemaV3<Iced, ModelImportV3> {

  // Input fields
  @API(help="Save imported model under given key into DKV.", json=false)
  public KeyV3.ModelKeyV3 model_id;

  @API(help="Source directory (hdfs, s3, local) containing serialized model")
  public String dir;

  @API(help="Override existing model in case it exists or throw exception if set to false")
  public boolean force = true;
}
