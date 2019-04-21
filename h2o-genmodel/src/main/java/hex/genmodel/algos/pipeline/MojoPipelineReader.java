package hex.genmodel.algos.pipeline;

import hex.genmodel.MojoModel;
import hex.genmodel.MultiModelMojoReader;

import java.util.LinkedList;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

public class MojoPipelineReader extends MultiModelMojoReader<MojoPipeline> {

  @Override
  public String getModelName() {
    return "MOJO Pipeline";
  }

  @Override
  protected void readParentModelData() {
    String mainModelAlias = readkv("main_model");
    String[] generatedColumns = readGeneratedColumns();

    _model._mainModel = getModel(mainModelAlias);
    _model._generatedColumnCount = generatedColumns.length;
    _model._targetMainModelRowIndices = new int[_model._mainModel._nfeatures - generatedColumns.length];
    _model._sourceRowIndices = findIndices(_model._names, _model._mainModel._names, _model._mainModel._nfeatures,
            _model._targetMainModelRowIndices, generatedColumns);

    Map<String, List<Integer>> m2idxs = readModel2GeneratedColumnIndex();

    _model._models = new MojoPipeline.PipelineSubModel[getSubModels().size() - 1];
    int modelsCnt = 0;
    int genColsCnt = 0;
    for (Map.Entry<String, MojoModel> subModel : getSubModels().entrySet()) {
      if (mainModelAlias.equals(subModel.getKey())) {
        continue;
      }
      final MojoModel m = subModel.getValue();
      final List<Integer> generatedColsIdxs = m2idxs.get(subModel.getKey());

      MojoPipeline.PipelineSubModel psm = _model._models[modelsCnt++] = new MojoPipeline.PipelineSubModel();
      psm._mojoModel = m;
      psm._inputMapping = mapModelColumns(m);
      psm._predsSize = m.getPredsSize(m.getModelCategory());
      psm._sourcePredsIndices = new int[generatedColsIdxs.size()];
      String[] targetColNames = new String[generatedColsIdxs.size()];
      int t = 0;
      for (int i : generatedColsIdxs) {
        psm._sourcePredsIndices[t] = readkv("generated_column_index_" + i, 0);
        targetColNames[t] = readkv("generated_column_name_" + i, "");
        t++;
      }
      psm._targetRowIndices = findIndices(_model._mainModel._names, targetColNames);
      genColsCnt += t;
    }
    assert modelsCnt == _model._models.length;
    assert genColsCnt == _model._generatedColumnCount;
  }

  private Map<String, List<Integer>> readModel2GeneratedColumnIndex() {
    final int cnt = readkv("generated_column_count", 0);
    Map<String, List<Integer>> map = new HashMap<>(cnt);
    for (int i = 0; i < cnt; i++) {
      String alias = readkv("generated_column_model_" + i);
      if (! map.containsKey(alias)) {
        map.put(alias, new LinkedList<Integer>());
      }
      List<Integer> indices = map.get(alias);
      indices.add(i);
    }
    return map;
  }

  private String[] readGeneratedColumns() {
    final int cnt = readkv("generated_column_count", 0);
    final String[] names = new String[cnt];
    for (int i = 0; i < names.length; i++) {
      names[i] = readkv("generated_column_name_" + i, "");
    }
    return names;
  }

  @Override
  protected MojoPipeline makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new MojoPipeline(columns, domains, responseColumn);
  }

  private int[] mapModelColumns(MojoModel subModel) {
    return findIndices(_model._names, subModel._names, subModel._nfeatures, null, new String[0]);
  }

  private static int[] findIndices(String[] strings, String[] subset) {
    return findIndices(strings, subset, subset.length, null, new String[0]);
  }

  private static int[] findIndices(String[] strings, String[] subset, int firstN, int[] outSubsetIdxs, String[] ignored) {
    final int[] idx = new int[firstN - ignored.length];
    assert outSubsetIdxs == null || outSubsetIdxs.length == idx.length;
    int cnt = 0;
    outer: for (int i = 0; i < firstN; i++) {
      final String s = subset[i];
      assert s != null;
      for (String si : ignored) {
        if (s.equals(si)) {
          continue outer;
        }
      }
      for (int j = 0; j < strings.length; j++) {
        if (s.equals(strings[j])) {
          if (outSubsetIdxs != null) {
            outSubsetIdxs[cnt] = i;
          }
          idx[cnt++] = j;
          continue outer;
        }
      }
      throw new IllegalStateException("Pipeline doesn't have input column '" + subset[i] + "'.");
    }
    assert cnt == idx.length;
    return idx;
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
