package water.api;

import water.api.ModelsHandler.Models;
import water.Key;
import water.Model;

abstract class ModelsBase extends Schema<Models, ModelsBase> {
  // Input fields
  @API(help="Key of Model of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  Key key;

  // Output fields
  @API(help="Models")
  ModelSchema[] models;

  // Non-version-specific filling into the impl
  @Override public Models createImpl() {
    Models m = new Models();
    m.key = this.key;

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
    this.key = m.key;

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
