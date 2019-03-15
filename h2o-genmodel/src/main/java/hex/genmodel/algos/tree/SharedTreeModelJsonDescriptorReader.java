package hex.genmodel.algos.tree;

import com.google.gson.JsonObject;
import hex.genmodel.descriptor.JsonModelDescriptorReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.descriptor.Table;
import hex.genmodel.descriptor.VariableImportances;

import java.util.Arrays;

/**
 * A reader of {@link hex.genmodel.ModelDescriptor} for all tree-based models inheriting from
 * the {@link SharedTreeMojoModel} class.
 */
public class SharedTreeModelJsonDescriptorReader extends JsonModelDescriptorReader {
    public SharedTreeModelJsonDescriptorReader(MojoReaderBackend mojoReaderBackend, MojoModel mojoModel) {
        super(mojoReaderBackend, mojoModel);
    }

    @Override
    protected void readModelSpecificDescription(JsonObject descriptionJson, ModelDescriptorBuilder modelDescriptorBuilder) {
        final VariableImportances variableImportances = extractVariableImportances(descriptionJson);
        modelDescriptorBuilder.variableImportances(variableImportances);
    }

    private VariableImportances extractVariableImportances(final JsonObject modelJson) {
        final Table table = extractTableFromJson(modelJson, "output.variable_importances");
        if (table == null) return null;
        final double[] relativeVarimps = new double[table.rows()];
        final int column = table.findColumnIndex("Relative Importance");
        if (column == -1) return null;
        for (int i = 0; i < table.rows(); i++) {
            relativeVarimps[i] = (double) table.getCell(column, i);
        }

        return new VariableImportances(Arrays.copyOf(_mojoModel._names, _mojoModel.nfeatures()), relativeVarimps);
    }


}
