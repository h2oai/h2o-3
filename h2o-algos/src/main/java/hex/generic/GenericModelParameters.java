package hex.generic;

import hex.Model;
import hex.genmodel.attributes.parameters.ColumnSpecifier;
import hex.genmodel.attributes.parameters.ModelParameter;
import hex.genmodel.attributes.parameters.ParameterKey;
import water.Iced;
import water.IcedWrapper;
import water.Key;
import water.api.schemas3.FrameV3;
import water.api.schemas3.ModelParameterSchemaV3;
import water.fvec.Frame;
import water.util.Log;

public class GenericModelParameters extends Model.Parameters {

    /**
     * Path of the file with embedded model
     */
    public String _path;

    /**
     * Key to the file with embedded model
     */
    public Key<Frame> _model_key;

    /**
     * Skip the check for white-listed algorithms, this allows load any MOJO.
     * Use at your own risk - unsupported.
     */
    public boolean _disable_algo_check;

    /**
     * Generic model parameters - might contain any parameters based on the state of the model in the training phase.
     */
    public ModelParameterSchemaV3[] _modelParameters;

    protected static ModelParameterSchemaV3[] convertParameters(final ModelParameter[] originalParams) {
        final ModelParameterSchemaV3[] convertedParams = new ModelParameterSchemaV3[originalParams.length];

        for (int i = 0; i < originalParams.length; i++) {
            final ModelParameter originalParam = originalParams[i];
            final ModelParameterSchemaV3 convertedParam = new ModelParameterSchemaV3();
            // Hand-built mapping for better performance compared to reflection
            convertedParam.name = originalParam.name;
            convertedParam.label = originalParam.label;
            convertedParam.is_mutually_exclusive_with = originalParam.is_mutually_exclusive_with;
            convertedParam.is_member_of_frames = originalParam.is_member_of_frames;
            convertedParam.values = originalParam.values;
            convertedParam.help = originalParam.help;
            convertedParam.level = originalParam.level;
            convertedParam.gridable = originalParam.gridable;
            convertedParam.required = originalParam.required;
            convertedParam.type = originalParam.type;
            convertedParam.actual_value = convertObjectToIced(originalParam.actual_value);
            convertedParam.default_value = convertObjectToIced(originalParam.default_value);
            convertedParam.input_value = convertObjectToIced(originalParam.input_value);

            convertedParams[i] = convertedParam;
        }

        return convertedParams;
    }

    private static Iced convertObjectToIced(final Object original) {
        final Iced converted;

        if (original == null) {
            converted = null;
        } else if (original instanceof ParameterKey) {
            final ParameterKey parameterKey = (ParameterKey) original;
            converted = Key.makeUserHidden(parameterKey.getName());
        } else if (original instanceof ColumnSpecifier) {
            final ColumnSpecifier columnSpecifier = (ColumnSpecifier) original;
            converted = new FrameV3.ColSpecifierV3(columnSpecifier.getColumnName(), columnSpecifier.getIsMemberOfFrames());
        } else {
            converted = new IcedWrapper(original);
        }

        return converted;
    }

    @Override
    public String algoName() {
        return "Generic";
    }

    @Override
    public String fullName() {
        return "Import MOJO Model";
    }

    @Override
    public String javaName() {
        return GenericModel.class.getName();
    }

    @Override
    public long progressUnits() {
        return 100;
    }
}
