package hex.tree.isofor;

import hex.genmodel.DefaultCategoricalEncoding;
import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

import static hex.tree.isofor.IsolationForestModel.IsolationForestParameters;
import static hex.tree.isofor.IsolationForestModel.IsolationForestOutput;

/**
 * Mojo definition for Isolation Forest model.
 */
public class IsolationForestMojoWriter extends SharedTreeMojoWriter<IsolationForestModel, IsolationForestParameters, IsolationForestOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public IsolationForestMojoWriter() {}

  public IsolationForestMojoWriter(IsolationForestModel model) { super(model); }

  @Override public String mojoVersion() {
    return "1.40";
  }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
    if (model.getGenModelEncoding() != DefaultCategoricalEncoding.AUTO) {
      throw new IllegalArgumentException("Only default categorical encoding scheme is supported for MOJO");
    }
    writekv("max_path_length", model._output._max_path_length);
    writekv("min_path_length", model._output._min_path_length);
    writekv("output_anomaly_flag", model.outputAnomalyFlag());
  }

}
