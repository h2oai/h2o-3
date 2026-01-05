package hex.coxph;

import hex.Model;
import hex.ModelMojoWriter;
import water.Scope;
import water.fvec.Frame;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedInt;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CoxPHMojoWriter extends ModelMojoWriter<CoxPHModel, CoxPHModel.CoxPHParameters, CoxPHModel.CoxPHOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public CoxPHMojoWriter() {}

  public CoxPHMojoWriter(CoxPHModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writeRectangularDoubleArray(model._output._x_mean_cat, "x_mean_cat");
    writeRectangularDoubleArray(model._output._x_mean_num, "x_mean_num");
    writekv("coef", model._output._coef);
    writekv("cats", model._output.data_info._cats);
    writekv("cat_offsets", model._output.data_info._catOffsets);
    writekv("use_all_factor_levels", model._output.data_info._useAllFactorLevels);
    writekv("num_numerical_columns", model._output.data_info._nums);
    writekv("num_offsets", model._output.data_info._numOffsets);
    writeStrata();
    writeInteractions();
  }

  private void writeStrata() throws IOException {
    final IcedHashMap<AstGroup.G, IcedInt> strataMap = model._output._strataMap;
    writekv("strata_count", strataMap.size());
    
    for (IcedHashMap.Entry<AstGroup.G, IcedInt> entry: strataMap.entrySet()) {
      writekv("strata_" + entry.getValue(), entry.getKey()._gs);
    }
  }

  private void writeInteractions() throws IOException {
    final Model.InteractionPair[] interactions = model._output.data_info._interactions;
    if (interactions == null || interactions.length == 0) {
      return;
    }

    final String[] columnNames = model.modelDescriptor().columnNames();
    int[] interaction_1 = new int[interactions.length];
    int[] interaction_2 = new int[interactions.length];
    int[] targets = new int[model._output.data_info._interactionVecs.length];
    String[] interaction_column_names = new String[interactions.length];
    List<String> allColNames = Arrays.asList(model._parms.train().names());
    Frame train = model._parms.train();
    Scope.track(train);
    for (int i = 0; i < interactions.length; i++) {
      interaction_1[i] = ArrayUtils.find(columnNames, interactions[i]._name1);
      interaction_2[i] = ArrayUtils.find(columnNames, interactions[i]._name2);
      String combinedName = interactions[i]._name1+"_"+interactions[i]._name2;
      targets[i] = ArrayUtils.find(columnNames, combinedName);  // column index in adaptedFrame
      interaction_column_names[i] = combinedName;
    }
    writekv("interactions_1", interaction_1);
    writekv("interactions_2", interaction_2);
    writeStringArrays(interaction_column_names, "interaction_column_names");
    writekv("interaction_targets", targets);  // specifies the position of the interaction column in input array
  }
}
