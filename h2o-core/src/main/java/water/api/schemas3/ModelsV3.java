package water.api.schemas3;

import hex.Model;
import water.api.*;
import water.api.ModelsHandler.Models;

public class ModelsV3 extends SchemaV3<Models, ModelsV3> {

  // Input fields
  @API(help="Name of Model of interest", json=false)
  public KeyV3.ModelKeyV3 model_id;

  @API(help="Return potentially abridged model suitable for viewing in a browser", json=false, required=false, direction=API.Direction.INPUT)
  public boolean preview = false;

  @API(help="Find and return compatible frames?", json=false, direction=API.Direction.INPUT)
  public boolean find_compatible_frames = false;

  // Output fields
  @API(help="Models", direction=API.Direction.OUTPUT)
  public ModelSchemaBaseV3[] models;

  @API(help="Compatible frames", direction=API.Direction.OUTPUT)
  public FrameV3[] compatible_frames; // TODO: FrameBaseV3

  // Non-version-specific filling into the impl
  @Override
  public Models fillImpl(Models m) {
    super.fillImpl(m);

    if (null != models) {
      m.models = new Model[models.length];

      int i = 0;
      for (ModelSchemaBaseV3 model : this.models) {
        m.models[i++] = (Model)model.createImpl();
      }
    }
    return m;
  }

  @Override
  public ModelsV3 fillFromImpl(Models m) {
    // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
    // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    this.model_id = new KeyV3.ModelKeyV3(m.model_id);
    this.find_compatible_frames = m.find_compatible_frames;

    if (null != m.models) {
      this.models = new ModelSchemaBaseV3[m.models.length];

      int i = 0;
      for (Model model : m.models) {
        this.models[i++] = (ModelSchemaV3)SchemaServer.schema(this.getSchemaVersion(), model).fillFromImpl(model);
      }
    }
    return this;
  }

  public ModelsV3 fillFromImplWithSynopsis(Models m) {
    this.model_id = new KeyV3.ModelKeyV3(m.model_id);
    if (null != m.models) {
      this.models = new ModelSchemaBaseV3[m.models.length];

      int i = 0;
      for (Model model : m.models) {
        this.models[i++] = new ModelSynopsisV3(model);
      }
    }
    return this;
  }
}
