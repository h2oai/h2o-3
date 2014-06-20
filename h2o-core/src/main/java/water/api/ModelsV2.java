package water.api;


import water.*;
import water.util.IcedHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

class ModelsV2 extends ModelsBase {
  // Input fields
  @API(help="Key of Model of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  Key key; // TODO: this should NOT appear in the output

  // Output fields
  @API(help="Map of (string representation of) key to Model summary.")
  IcedHashMap<String, ModelSummaryV2> models;

  @API(help="Map of (string representation of) key to Frame summary.")
  IcedHashMap<String, FramesV2.FrameSummaryV2> frames;

  @API(help="General information on the response.")
  ResponseInfoV2 response;

  /**
   * Schema for the simple Model summary representation used (only) in /2/Models and
   * /2/Frames.
   */
  static final class ModelSummaryV2 extends Schema {
    @API(help="String representation of the Model's key.")
    String key;

    @API(help="Model algorithm, something like \"GLM\" or \"DeepLearning\".", json=true)
    public String model_algorithm = "unknown";

    @API(help="\"Binomial\", \"Multinomial\", \"Regression\" or \"Clustering\".", json=true)
    public Model.ModelCategory model_category = Model.ModelCategory.Unknown;

    @API(help="Job state (one of CREATED, RUNNING, CANCELLED, FAILED or DONE).", json=true)
    public Job.JobState state = Job.JobState.CREATED;

    @API(help="Unique ID for the model, used to distinguish between different models built with the same key (name).", json=true)
    public String id = null;

    @API(help="Creation time in milliseconds since Jan 1, 1970.", json=true)
    public long creation_epoch_time_millis = -1;

    @API(help="Model training duration in milliseconds.", json=true)
    public long training_duration_in_ms = -1;

    @API(help="Model input column names.", json=true)
    public String[] input_column_names = new String[0];

    @API(help="Model response column name.", json=true)
    public String response_column_name = "unknown";

    @API(help="List of the model parameters which are most critical for model quality.", json=true)
    public IcedHashMap critical_parameters = new IcedHashMap<String, Object>();

    @API(help="List of model parameters which are secondary in imporance for model quality.", json=true)
    public IcedHashMap secondary_parameters = new IcedHashMap<String, Object>();

    @API(help="List of model parameters which are generally used only by experts for special cases.", json=true)
    public IcedHashMap expert_parameters = new IcedHashMap<String, Object>();

    @API(help="Compute and return variable importances.", json=true)
    public VarImp variable_importances = null;

    @API(help="List of keys of frames that are compatible with this model.", json=true)
    public String[] compatible_frames = new String[0];

    // Collect in here, and then put into the array.
    transient private Set<String> compatible_frames_set = new HashSet<String>();


    ModelSummaryV2(Model model) {
      this.key = model._key.toString();

      this.model_algorithm = "unknown"; // TODO
      this.model_category = model.getModelCategory(); // TODO
      this.state = Job.JobState.FAILED; // TODO
      this.id = "deadbeefcafed00d"; // TODO
      this.creation_epoch_time_millis = -1; // TODO
      this.training_duration_in_ms = -1; // TODO

      this.input_column_names = new String[model.nfeatures() - 1];
      System.arraycopy(model.allNames(), 0, this.input_column_names, 0, model.nfeatures() - 1);
      this.response_column_name = model.responseName();

      this.critical_parameters = new IcedHashMap<String, Object>(); // TODO
      this.secondary_parameters = new IcedHashMap<String, Object>(); // TODO
      this.expert_parameters = new IcedHashMap<String, Object>(); // TODO
      this.variable_importances = null; // TODO
    }

    @Override protected ModelSummaryV2 fillInto( Handler h ) { throw H2O.fail("fillInto should never be called on ModelSummaryV2"); }
    @Override protected ModelSummaryV2 fillFrom( Handler h ) { throw H2O.fail("fillFrom should never be called on ModelSummaryV2"); }
  }


  // Version-specific filling into the handler
  @Override protected ModelsBase fillInto( ModelsHandler h ) {
    h.key = this.key;

    if (null != models) {
      h.models = new Model[models.size()];

      int i = 0;
      for (ModelSummaryV2 model : this.models.values()) {
        h.models[i++] = ModelsHandler.getFromDKV(model.key);
      }
    }
    return this;
  }

  // Version & Schema-specific filling from the handler
  @Override protected ModelsBase fillFrom( ModelsHandler h ) {
    this.key = h.key;

    this.models = new IcedHashMap<String, ModelSummaryV2>();
    if (null != h.models) {
      for (Model model : h.models) {
        this.models.put(model._key.toString(), new ModelSummaryV2(model));
      }
    }

    // TODO:
    this.frames = new IcedHashMap<String, FramesV2.FrameSummaryV2>();

    // TODO:
    this.response = new ResponseInfoV2();

    return this;
  }
}
