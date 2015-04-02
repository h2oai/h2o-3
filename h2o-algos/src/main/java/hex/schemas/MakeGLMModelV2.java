package hex.schemas;

import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import water.DKV;
import water.Iced;
import water.Key;
import water.api.API;
import water.api.API.Direction;
import water.api.API.Level;
import water.api.KeyV1.FrameKeyV1;
import water.api.Schema;
import water.api.SupervisedModelParametersSchema;
import water.fvec.Frame;

/**
 * Created by tomasnykodym on 8/29/14.
 *
 * End point to update a model. Creates a modified copy of the original model. Can only change coefficient values.
 */
public class MakeGLMModelV2 extends Schema<Iced,MakeGLMModelV2> {

  @API(help="source model", required = true, direction = Direction.INPUT)
  public Key<Model> model;

  @API(help="destination key", required = false, direction = Direction.INPUT)
  public Key dest = Key.make();

  @API(help="coefficient names", required = true, direction = Direction.INPUT)
  public String [] names;

  @API(help = "new glm coefficients", required = true, direction = Direction.INPUT)
  public double [] beta;

  @API(help="decision threshold for label-generation", required = false, direction = Direction.INPUT)
  public float threshold = .5f;

}
