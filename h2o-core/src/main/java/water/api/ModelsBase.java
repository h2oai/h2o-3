package water.api;

import hex.Model;
import water.api.KeyV3.ModelKeyV3;
import water.api.ModelsHandler.Models;

class ModelsBase<I extends Models, S extends ModelsBase<I, S>> extends SchemaV3<I, ModelsBase<I, S>> {
  // Input fields
  @API(help="Name of Model of interest", json=false)
  public ModelKeyV3 model_id;

  @API(help="Return potentially abridged model suitable for viewing in a browser", json=false, required=false, direction=API.Direction.INPUT)
  public boolean preview = false;

  @API(help="Find and return compatible frames?", json=false, direction=API.Direction.INPUT)
  public boolean find_compatible_frames = false;

  // Output fields
  @API(help="Models", direction=API.Direction.OUTPUT)
  public ModelSchemaBase[] models;

  @API(help="Compatible frames", direction=API.Direction.OUTPUT)
  FrameV3[] compatible_frames; // TODO: FrameBase

  // Non-version-specific filling into the impl
  @Override public I fillImpl(I m) {
    super.fillImpl(m);

    if (null != models) {
      m.models = new Model[models.length];

      int i = 0;
      for (ModelSchemaBase model : this.models) {
        m.models[i++] = (Model)model.createImpl();
      }
    }
    return m;
  }

  @Override public ModelsBase fillFromImpl(Models m) {
    // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
    // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    this.model_id = new ModelKeyV3(m.model_id);
    this.find_compatible_frames = m.find_compatible_frames;

    if (null != m.models) {
      this.models = new ModelSchemaBase[m.models.length];

      int i = 0;
      for (Model model : m.models) {
        this.models[i++] = (ModelSchema)SchemaServer.schema(this.getSchemaVersion(), model).fillFromImpl(model);
      }
    }
    return this;
  }

  public ModelsBase fillFromImplWithSynopsis(Models m) {
    this.model_id = new ModelKeyV3(m.model_id);
    if (null != m.models) {
      this.models = new ModelSchemaBase[m.models.length];

      int i = 0;
      for (Model model : m.models) {
        this.models[i++] = new ModelSynopsisV3(model);
      }
    }
    return this;
  }
}
