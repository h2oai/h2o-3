package water.api;

import water.Key;
import water.Model;

// class ModelsBase<H extends Handler<H,S>, S extends Schema<H,S>> extends Schema<H, S> {
abstract class ModelsBase extends Schema<ModelsHandler, ModelsBase> {
  // Input fields
  @API(help="Key of Model of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  Key key;

  // Output fields
  @API(help="Models")
  // TODO: change to ModelsV3:
  ModelsV2.ModelSummaryV2[] models; // TODO: create interface or superclass (e.g., ModelBase) for ModelV2

  // Non-version-specific filling into the handler
  @Override protected ModelsBase fillInto( ModelsHandler h ) {
    h.key = this.key;

    if (null != models) {
      h.models = new Model[models.length];

      int i = 0;
      for (ModelsV2.ModelSummaryV2 model : this.models) {
        // TODO: h.models[i++] = model._fr;
      }
    }
    return this;
  }

  // TODO: parameterize on the ModelVx Schema class
  @Override protected ModelsBase fillFrom( ModelsHandler h ) {
    this.key = h.key;

/* TODO
    if (null != h.models) {
      this.models = new ModelV2[h.models.length];

      int i = 0;
      for (Model model : h.models) {
        this.models[i++] = new ModelV2(model);
      }
    }
    */
    return this;
  }
}
