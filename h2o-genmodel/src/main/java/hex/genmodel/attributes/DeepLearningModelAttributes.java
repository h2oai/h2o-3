package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;

public class DeepLearningModelAttributes extends ModelAttributes {

    private final VariableImportances _variableImportances;

    public DeepLearningModelAttributes(MojoModel model, JsonObject modelJson) {
        super(model, modelJson);
        _variableImportances = VariableImportances.extractFromJson(modelJson);
    }
    
    public VariableImportances getVariableImportances(){
        return _variableImportances;
    }
}
