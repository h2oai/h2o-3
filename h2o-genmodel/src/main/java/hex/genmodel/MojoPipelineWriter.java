package hex.genmodel;

import hex.ModelCategory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class MojoPipelineWriter extends AbstractMojoWriter {

  private Map<String, MojoModel> _models;
  private Map<String, String> _inputMapping;
  private String _mainModelAlias;

  MojoPipelineWriter(Map<String, MojoModel> models, Map<String, String> inputMapping, String mainModelAlias) {
    super(makePipelineDescriptor(models, inputMapping, mainModelAlias));
    _models = models;
    _inputMapping = inputMapping;
    _mainModelAlias = mainModelAlias;
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("submodel_count", _models.size());
    int modelNum = 0;
    for (Map.Entry<String, MojoModel> model : _models.entrySet()) {
      writekv("submodel_key_" + modelNum, model.getKey());
      writekv("submodel_dir_" + modelNum, "models/" + model.getKey() + "/");
      modelNum++;
    }
    writekv("generated_column_count", _inputMapping.size());
    int generatedColumnNum = 0;
    for (Map.Entry<String, String> mapping : _inputMapping.entrySet()) {
      String inputSpec = mapping.getValue();
      String[] inputSpecArr = inputSpec.split(":", 2);
      writekv("generated_column_name_" + generatedColumnNum, mapping.getKey());
      writekv("generated_column_model_" + generatedColumnNum, inputSpecArr[0]);
      writekv("generated_column_index_" + generatedColumnNum, Integer.valueOf(inputSpecArr[1]));
      generatedColumnNum++;
    }
    writekv("main_model", _mainModelAlias);
  }

  private static MojoPipelineDescriptor makePipelineDescriptor(
          Map<String, MojoModel> models, Map<String, String> inputMapping, String mainModelAlias) {
    MojoModel finalModel = models.get(mainModelAlias);
    if (finalModel == null) {
      throw new IllegalArgumentException("Main model is missing. There is no model with alias '" + mainModelAlias + "'.");
    }
    LinkedHashMap<String, String[]> schema = deriveInputSchema(models, inputMapping, finalModel);
    return new MojoPipelineDescriptor(schema, finalModel);
  }

  private static LinkedHashMap<String, String[]> deriveInputSchema(
          Map<String, MojoModel> allModels, Map<String, String> inputMapping, MojoModel finalModel) {
    LinkedHashMap<String, String[]> schema = new LinkedHashMap<>();

    for (MojoModel model : allModels.values()) {
      if (model == finalModel) {
        continue;
      }
      for (int i = 0; i < model.nfeatures(); i++) {
        String fName = model._names[i];
        if (schema.containsKey(fName)) { // make sure the domain matches
          String[] domain = schema.get(fName);
          if (! Arrays.equals(domain, model._domains[i])) {
            throw new IllegalStateException("Domains of column '" + fName + "' differ.");
          }
        } else {
          schema.put(fName, model._domains[i]);
        }
      }
    }

    for (int i = 0; i < finalModel._names.length; i++) { // we include the response of the final model as well
      String fName = finalModel._names[i];
      if (! inputMapping.containsKey(fName)) {
        schema.put(fName, finalModel._domains[i]);
      }
    }

    return schema;
  }

  private static class MojoPipelineDescriptor implements ModelDescriptor {

    private final MojoModel _finalModel;
    private final String[] _names;
    private final String[][] _domains;

    private MojoPipelineDescriptor(LinkedHashMap<String, String[]> schema, MojoModel finalModel) {
      _finalModel = finalModel;
      _names = new String[schema.size()];
      _domains = new String[schema.size()][];
      int i = 0;
      for (Map.Entry<String, String[]> field : schema.entrySet()) {
        _names[i] = field.getKey();
        _domains[i] = field.getValue();
        i++;
      }
    }

    @Override
    public String[][] scoringDomains() {
      return _domains;
    }

    @Override
    public String projectVersion() {
      return _finalModel._h2oVersion;
    }

    @Override
    public String algoName() {
      return "pipeline";
    }

    @Override
    public String algoFullName() {
      return "MOJO Pipeline";
    }

    @Override
    public ModelCategory getModelCategory() {
      return _finalModel._category;
    }

    @Override
    public boolean isSupervised() {
      return _finalModel.isSupervised();
    }

    @Override
    public int nfeatures() {
      return isSupervised() ? columnNames().length - 1 : columnNames().length;
    }

    @Override
    public int nclasses() {
      return _finalModel.nclasses();
    }

    @Override
    public String[] columnNames() {
      return _names;
    }

    @Override
    public boolean balanceClasses() {
      return _finalModel._balanceClasses;
    }

    @Override
    public double defaultThreshold() {
      return _finalModel._defaultThreshold;
    }

    @Override
    public double[] priorClassDist() {
      return _finalModel._priorClassDistrib;
    }

    @Override
    public double[] modelClassDist() {
      return _finalModel._modelClassDistrib;
    }

    @Override
    public String uuid() {
      return _finalModel._uuid;
    }

    @Override
    public String timestamp() {
      return String.valueOf(new Date().getTime());
    }
  }

}
