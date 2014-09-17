package water.api;

import hex.Model;
import water.Key;
import water.api.ModelsHandler.Models;

abstract class ModelsBase extends Schema<Models, ModelsBase> {
  // Input fields
  @API(help="Key of Model of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  public Key key;

  @API(help="Find and return compatible frames?", json=false)
  public boolean find_compatible_frames = false;

  // Output fields
  @API(help="Models", direction = API.Direction.OUTPUT)
  public ModelSchema[] models;

  // Non-version-specific filling into the impl
  @Override public Models createImpl() {
    Models m = new Models();
    // TODO: this is failing in BeanUtils with an IllegalAccessException.  Why?  Different class loaders?
    // BeanUtils.copyProperties(m, this, BeanUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    m.key = this.key;
    m.find_compatible_frames = this.find_compatible_frames;

    if (null != models) {
      m.models = new Model[models.length];

      int i = 0;
      for (ModelSchema model : this.models) {
        m.models[i++] = model.createImpl();
      }
    }
    return m;
  }

  @Override public ModelsBase fillFromImpl(Models m) {
    // TODO: this is failing in BeanUtils with an IllegalAccessException.  Why?  Different class loaders?
    // BeanUtils.copyProperties(this, m, BeanUtils.FieldNaming.CONSISTENT);

    // Shouldn't need to do this manually. . .
    this.key = m.key;
    this.find_compatible_frames = m.find_compatible_frames;

    if (null != m.models) {
      this.models = new ModelSchema[m.models.length];

      int i = 0;
      for (Model model : m.models) {
        this.models[i++] = model.schema().fillFromImpl(model);
      }
    }
    return this;
  }
}
