package hex;

import water.Job;
import water.api.Handler;
import water.api.JobV3;
import water.api.ModelParametersSchema;
import water.api.Schema;
import water.fvec.Frame;

/**
 * FIXME: how to get rid of P, since it is already enforced by S
 *
 * @param <G>  Implementation output of grid search
 * @param <P>  Provides additional type information about grid search parameters.
 * @param <S>  Input/output schema produced by this grid search handler (IN: parameters, OUT: parameters + job)
 */
public abstract class GridSearchHandler< G extends Grid<MP, G>,
                                S extends GridSearchSchema<G,S, MP, P>,
                                MP extends Model.Parameters,
                                P extends ModelParametersSchema> extends Handler {

  public S do_train(int version, S gridSearchSchema) { // just following convention of model builders
    // Extract input parameters
    P parameters = gridSearchSchema.parameters;
    // TODO: Verify inputs, make sure to reject wrong training_frame

    // Get/create a grid for given frame
    G gridBuilder = gridSearchSchema.fillImpl(createGrid(parameters.training_frame.key().get()));
    // Start grid search and return the schema back with job key
    Grid.GridSearch gs = gridBuilder.startGridSearch((MP) parameters.createAndFillImpl(),
                                                      gridSearchSchema.grid_parameters);
    // Fill schema with job parameters
    // FIXME: right now we have to remove grid parameters which we sent
    gridSearchSchema.grid_parameters = null;
    gridSearchSchema.total_models = gs._total_models;
    gridSearchSchema.job = (JobV3) Schema.schema(version, Job.class).fillFromImpl(gs);

    return gridSearchSchema;
  }

  // Force underlying handlers to create their grid implementations
  // - In the most of cases the call needs to be forwarded to GridSearch factory
  protected abstract G createGrid(Frame f);
}

