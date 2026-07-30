package hex.schemas;

import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

/**
 * End point to get derived GLM model when control variables or remove offset effects features
 * are enabled. Creates a modified copy of the original model. When the source model was trained
 * with cross-validation, also reconstructs the corresponding CV metric view (control-vars-only-
 * restricted, offset-only-restricted, or fully-unrestricted, depending on which flags are set)
 * as the derived model's cross_validation_metrics / cross_validation_metrics_summary.
 */
public class MakeDerivedGLMModelV3 extends SchemaV3<Iced, MakeDerivedGLMModelV3> {

    @API(help = "source model", required = true, direction = API.Direction.INPUT)
    public KeyV3.ModelKeyV3 model;

    @API(help = "destination key", required = false, direction = API.Direction.INPUT)
    public String dest;

    @API(help = "When true, exclude the offset effects from the derived model's scoring and metrics; " +
            "the control-variables effects (if any) stay included. Requires the source model to have " +
            "been trained with remove_offset_effects=true. Cannot be combined with " +
            "remove_control_variables_effects.", required = false, direction = API.Direction.INPUT)
    public boolean remove_offset_effects;

    @API(help = "When true, exclude the control-variables effects from the derived model's scoring and " +
            "metrics; the offset effects (if any) stay included. Requires the source model to have been " +
            "trained with control_variables set. Cannot be combined with remove_offset_effects.",
            required = false, direction = API.Direction.INPUT)
    public boolean remove_control_variables_effects;
}
