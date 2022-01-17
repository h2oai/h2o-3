package hex.genmodel.algos.isofor;

import hex.genmodel.algos.tree.SharedTreeMojoReader;

import java.io.IOException;

public class IsolationForestMojoReader extends SharedTreeMojoReader<IsolationForestMojoModel> {

  @Override
  public String getModelName() {
    return "Isolation Forest";
  }

  @Override
  protected String getModelMojoReaderClassName() { return "hex.tree.isofor.IsolationForestMojoWriter"; }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
    super.readModelData(readModelMetadata);
    _model._min_path_length = readkv("min_path_length", 0);
    _model._max_path_length = readkv("max_path_length", 0);
    _model._outputAnomalyFlag = readkv("output_anomaly_flag", false);
  }

  @Override
  protected IsolationForestMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new IsolationForestMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.40";
  }

}
