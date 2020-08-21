package hex.util;

import hex.Model;
import hex.ModelBuilder;
import water.Value;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.PojoUtils;

import java.lang.reflect.Field;
import java.util.Arrays;

public class CheckpointUtils {

    /**
     * This method will take actual parameters and validate them with parameters of
     * requested checkpoint. In case of problem, it throws an API exception.
     *
     * @param params               model parameters
     * @param nonModifiableFields  params if changed will raise an error
     * @param checkpointParameters checkpoint parameters
     */
    private static void validateWithCheckpoint(
        Model.Parameters params,
        String[] nonModifiableFields,
        Model.Parameters checkpointParameters
    ) {
        for (Field fAfter : params.getClass().getFields()) {
            // only look at non-modifiable fields
            if (ArrayUtils.contains(nonModifiableFields, fAfter.getName())) {
                for (Field fBefore : checkpointParameters.getClass().getFields()) {
                    if (fBefore.equals(fAfter)) {
                        try {
                            if (!PojoUtils.equals(params, fAfter, checkpointParameters, checkpointParameters.getClass().getField(fAfter.getName()))) {
                                throw new H2OIllegalArgumentException(fAfter.getName(), "TreeBuilder", "Field " + fAfter.getName() + " cannot be modified if checkpoint is specified!");
                            }
                        } catch (NoSuchFieldException e) {
                            throw new H2OIllegalArgumentException(fAfter.getName(), "TreeBuilder", "Field " + fAfter.getName() + " is not supported by checkpoint!");
                        }
                    }
                }
            }
        }
    }

    private static void validateNTrees(ModelBuilder builder, Model.GetNTrees params, Model.GetNTrees output) {
        if (params.getNTrees() < output.getNTrees() + 1) {
            builder.error("_ntrees", "If checkpoint is specified then requested ntrees must be higher than " + (output.getNTrees() + 1));
        }
    }

    public static <M extends Model<M, P, O>, P extends Model.Parameters, O extends Model.Output> M getAndValidateCheckpointModel(
        ModelBuilder<M, P, O> builder,
        String[] nonModifiableFields,
        Value cv
    ) {
        M checkpointModel = cv.get();
        try {
            validateWithCheckpoint(builder._parms, nonModifiableFields, checkpointModel._input_parms);
            if (builder.isClassifier() != checkpointModel._output.isClassifier())
                throw new IllegalArgumentException("Response type must be the same as for the checkpointed model.");
            if (!Arrays.equals(builder.train().names(), checkpointModel._output._names)) {
                throw new IllegalArgumentException("The columns of the training data must be the same as for the checkpointed model");
            }
            if (!Arrays.deepEquals(builder.train().domains(), checkpointModel._output._domains)) {
                throw new IllegalArgumentException("Categorical factor levels of the training data must be the same as for the checkpointed model");
            }
        } catch (H2OIllegalArgumentException e) {
            builder.error(e.values.get("argument").toString(), e.values.get("value").toString());
        }
        if (builder._parms instanceof Model.GetNTrees && checkpointModel._output instanceof Model.GetNTrees) {
            validateNTrees(builder, (Model.GetNTrees) builder._parms, (Model.GetNTrees) checkpointModel._output);
        }
        return checkpointModel;
    }


}
