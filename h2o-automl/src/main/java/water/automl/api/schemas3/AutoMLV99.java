package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLV99 extends SchemaV3<AutoML,AutoMLV99> {
  @API(help="The AutoML key",direction=API.Direction.INPUT)
  public AutoML.AutoMLKeyV3 automl_id;

  @API(help="the leader model's key", direction=API.Direction.OUTPUT)
  public KeyV3.ModelKeyV3   leader;


  @Override public AutoMLV99 fillFromImpl(AutoML m) {
    super.fillFromImpl(m);
    if (null != m._key) {
      this.automl_id = new AutoML.AutoMLKeyV3(m._key);
      if (null != m._key.get().leaderboard() && null != m._key.get().leaderboard().leader()) {
        this.leader = new KeyV3.ModelKeyV3(m._key.get().leaderboard().leader()._key);
      }
    }

    return this; // have to cast because the definition of S doesn't include ModelSchemaV3
  }
}
