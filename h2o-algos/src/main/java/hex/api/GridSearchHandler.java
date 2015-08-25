package hex.api;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hex.Model;
import hex.ModelParametersBuilderFactory;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.ModelFactory;
import hex.schemas.GridSearchSchema;
import water.Job;
import water.Key;
import water.api.API;
import water.api.Handler;
import water.api.JobV3;
import water.api.ModelParametersSchema;
import water.api.Schema;
import water.api.SchemaMetadata;
import water.exceptions.H2OIllegalArgumentException;
import water.util.PojoUtils;

/**
 * A generic grid search handler implementing launch of grid search.
 *
 * <p>A model specific grid search handlers should inherit from class and implements corresponding
 * methods.
 *
 * FIXME: how to get rid of P, since it is already enforced by S
 *
 * @param <G>  Implementation output of grid search
 * @param <MP> Type of model parameters
 * @param <P>  Type of schema representing model parameters
 * @param <S>  Schema representing structure of grid search end-point
 */
public abstract class GridSearchHandler<G extends Grid<MP>,
    S extends GridSearchSchema<G, S, MP, P>,
    MP extends Model.Parameters,
    P extends ModelParametersSchema> extends Handler {

  public S do_train(int version,
                    S gridSearchSchema) { // just following convention of model builders
    // Extract input parameters
    P parametersSchema = gridSearchSchema.parameters;
    // TODO: Verify algorithm inputs, make sure to reject wrong training_frame
    // Extract hyper parameters
    Map<String, Object[]> hyperParams = gridSearchSchema.hyper_parameters;
    // Verify list of hyper parameters
    // Right now only names, no types
    validateHyperParams(parametersSchema, hyperParams);

    // Get/create a grid for given frame
    // FIXME: Grid ID is not pass to grid search builder!
    Key<Grid> destKey = gridSearchSchema.grid_id != null ? gridSearchSchema.grid_id.key() : null;
    // Get actual parameters
    MP params = (MP) parametersSchema.createAndFillImpl();
    // Create target grid search object (keep it private for now)
    // Start grid search and return the schema back with job key
    ModelFactory<MP> modelFactory = getModelFactory();
    GridSearch
        gsJob = GridSearch.startGridSearch(destKey,
                                           params,
                                           hyperParams,
                                           modelFactory,
                                           new DefaultModelParametersBuilderFactory<MP, P>());

    // Fill schema with job parameters
    // FIXME: right now we have to remove grid parameters which we sent back
    gridSearchSchema.hyper_parameters = null;
    gridSearchSchema.total_models = gsJob.getModelCount();
    gridSearchSchema.job = (JobV3) Schema.schema(version, Job.class).fillFromImpl(gsJob);

    return gridSearchSchema;
  }

  // Force underlying handlers to create their grid implementations
  // - In the most of cases the call needs to be forwarded to GridSearch factory
  protected abstract ModelFactory<MP> getModelFactory();

  /**
   * Validate given hyper parameters with respect to type parameter P.
   *
   * It verifies that given parameters are annotated in P with @API annotation
   *
   * @param params      regular model build parameters
   * @param hyperParams map of hyper parameters
   */
  protected void validateHyperParams(P params, Map<String, Object[]> hyperParams) {
    List<SchemaMetadata.FieldMetadata> fsMeta = SchemaMetadata.getFieldMetadata(params);
    for (Map.Entry<String, Object[]> hparam : hyperParams.entrySet()) {
      SchemaMetadata.FieldMetadata fieldMetadata = null;
      // Found corresponding metadata about the field
      for (SchemaMetadata.FieldMetadata fm : fsMeta) {
        if (fm.name.equals(hparam.getKey())) {
          fieldMetadata = fm;
          break;
        }
      }
      if (fieldMetadata == null) {
        throw new H2OIllegalArgumentException(hparam.getKey(), "grid",
                                              "Unknown hyper parameter for grid search!");
      }
      if (!fieldMetadata.is_gridable) {
        throw new H2OIllegalArgumentException(hparam.getKey(), "grid",
                                              "Illegal hyper parameter for grid search! The parameter '"
                                              + fieldMetadata.name + " is not gridable!");
      }
    }
  }


  static class DefaultModelParametersBuilderFactory<MP extends Model.Parameters, PS extends ModelParametersSchema>
      implements ModelParametersBuilderFactory<MP> {

    @Override
    public ModelParametersBuilder<MP> get(MP initialParams) {
      return new ModelParametersFromSchemaBuilder<MP, PS>(initialParams);
    }

    @Override
    public PojoUtils.FieldNaming getFieldNamingStrategy() {
      return PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES;
    }
  }

  /**
   * Model parameters factory building model parameters with respect to its schema. <p> A user calls
   * the {@link #set(String, Object)} method with names of parameters as they are defined in Schema.
   * The builder transfer the given values from Schema to corresponding model parameters object.
   * </p>
   *
   * @param <MP> type of model parameters
   * @param <PS> type of schema representing model parameters
   */
  public static class ModelParametersFromSchemaBuilder<MP extends Model.Parameters, PS extends ModelParametersSchema>
      implements ModelParametersBuilderFactory.ModelParametersBuilder<MP> {

    final private MP params;
    final private PS paramsSchema;
    final private ArrayList<String> fields;

    public ModelParametersFromSchemaBuilder(MP initialParams) {
      params = initialParams;
      paramsSchema = (PS) Schema.schema(Schema.getHighestSupportedVersion(), params.getClass());
      fields = new ArrayList<>(7);
    }

    public ModelParametersFromSchemaBuilder<MP, PS> set(String name, Object value) {
      try {
        Field f = paramsSchema.getClass().getField(name);
        API api = (API) f.getAnnotations()[0];
        Schema.setField(paramsSchema, f, name, value.toString(), api.required(),
                        paramsSchema.getClass());
        fields.add(name);
      } catch (NoSuchFieldException e) {
        throw new IllegalArgumentException("Cannot find field '" + name + "'", e);
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException("Cannot set field '" + name + "'", e);
      }
      return this;
    }

    public MP build() {
      PojoUtils
          .copyProperties(params, paramsSchema, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES, null,
                          fields.toArray(new String[fields.size()]));
      // FIXME: handle these train/valid fields in different way
      // See: ModelParametersSchema#fillImpl
      if (params._valid == null && paramsSchema.validation_frame != null) {
        params._valid = Key.make(paramsSchema.validation_frame.name);
      }

      return params;
    }
  }
}





