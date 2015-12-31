package water.api;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelParametersBuilderFactory;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.schemas.GridSearchSchema;
import water.H2O;
import water.Job;
import water.Key;
import water.TypeMap;
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
public class GridSearchHandler<G extends Grid<MP>,
    S extends GridSearchSchema<G, S, MP, P>,
    MP extends Model.Parameters,
    P extends ModelParametersSchema> extends Handler {

  // Invoke the handler with parameters.  Can throw any exception the called handler can throw.
  @Override Schema handle(int version, water.api.Route route, Properties parms) throws Exception {
    // Only here for train or validate-parms
    if( !route._handler_method.getName().equals("train") )
      throw water.H2O.unimpl();

    // Peek out the desired algo from the URL
    String ss[] = route._url_pattern_raw.split("/");
    String algoURLName = ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
    String algoName = ModelBuilder.algoName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning

    // Build a Model Schema and a ModelParameters Schema
    String schemaName = "hex.schemas.GridSchemaV"+version;
    Schema schema = (Schema) TypeMap.newFreezable(schemaName);
    schema.init_meta();

    throw H2O.unimpl();
//    String grid_id = (String)parms.get("grid_id");
//    Key<Grid> key = (Key<Grid>)(grid_id==null ? GridSearch.getKeyName(algoName,frame) : Key.<Grid>make(grid_id));
//    G grid = ...;
//    B builder = ModelBuilder.make(algoURLName,job,key);
//    schema.parameters.fillFromImpl(builder._parms); // Defaults for this builder into schema
//    schema.parameters.fillFromParms(parms);         // Overwrite with user parms
//    schema.parameters.fillImpl(builder._parms);     // Merged parms back over Model.Parameter object
//    builder.init(false);          // validate parameters
//    if (builder.error_count() > 0)// Check for any parameter errors and bail now
//      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(builder);
//
//    _t_start = System.currentTimeMillis();
//    builder.trainModel();
//    _t_stop  = System.currentTimeMillis();
//
//    schema.fillFromImpl(builder); // Fill in the result Schema with the Job at least, plus any extra trainModel errors
//    PojoUtils.copyProperties(schema.parameters, builder._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, null, new String[] { "error_count", "messages" });
//    schema.setHttpStatus(HttpResponseStatus.OK.getCode());
//    return schema;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public S train(int version, S gridSearchSchema) {
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
    Job<Grid> gsJob = GridSearch.startGridSearch(destKey,
                                                 params,
                                                 hyperParams,
                                                 new DefaultModelParametersBuilderFactory<MP, P>());

    // Fill schema with job parameters
    // FIXME: right now we have to remove grid parameters which we sent back
    gridSearchSchema.hyper_parameters = null;
    gridSearchSchema.total_models = gsJob._result.get().getModelCount();
    gridSearchSchema.job = (JobV3) Schema.schema(version, Job.class).fillFromImpl(gsJob);

    return gridSearchSchema;
  }

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
      } catch (RuntimeException e) {
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
      if (params._train == null && paramsSchema.training_frame != null) {
        params._train = Key.make(paramsSchema.training_frame.name);
      }

      return params;
    }
  }
}
