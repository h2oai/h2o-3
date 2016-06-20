package water.api;

import hex.Model;
import water.api.KeyV3.ModelKeyV3;
import water.api.KeyV3.FrameKeyV3;

/**
 * A Model schema contains all the pieces associated with a Model:
 * <p>
 * <ul>
 * <li> an instance of a ModelParameters schema containing the build parameters</li>
 * <li> an instance of a ModelResults schema containing the f00 b4r b4z</li>
 * <li> an instance of a ModelMetrics schema</li>
 * </ul>
 *
 */
public class ModelSchemaBase<M extends water.Iced, S extends ModelSchemaBase<M, S>> extends SchemaV3<M, S> {
  public ModelSchemaBase() { }

  // Input fields
  @API(help="Model key", required=true, direction=API.Direction.INOUT)
  public ModelKeyV3 model_id;

  // Output fields
  @API(help="The algo name for this Model.", direction=API.Direction.OUTPUT)
  public String algo;

  @API(help="The pretty algo name for this Model (e.g., Generalized Linear Model, rather than GLM).", direction=API.Direction.OUTPUT)
  public String algo_full_name;

  @API(help="The response column name for this Model (if applicable). Is null otherwise.", direction=API.Direction.OUTPUT)
  public String response_column_name;

  @API(help="The Model\'s training frame key", direction=API.Direction.OUTPUT)
  public FrameKeyV3 data_frame;

  @API(help="Timestamp for when this model was completed", direction=API.Direction.OUTPUT)
  public long timestamp;

  public ModelSchemaBase(Model m) {
    super();
    this.model_id = new ModelKeyV3(m._key);
    this.algo = m._parms.algoName().toLowerCase();
    this.algo_full_name = m._parms.fullName();
    this.data_frame = new FrameKeyV3(m._parms._train);
    this.response_column_name = m._parms._response_column;
    this.timestamp = m._output._job == null?-1:m._output._job.isRunning() ? 0 : m._output._job.end_time();
  }
}
