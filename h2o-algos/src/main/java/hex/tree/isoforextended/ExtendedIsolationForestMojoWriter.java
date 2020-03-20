package hex.tree.isoforextended;

import hex.tree.SharedTreeMojoWriter;
import hex.tree.isofor.IsolationForestModel;

import java.io.IOException;

import static hex.tree.isofor.IsolationForestModel.IsolationForestOutput;
import static hex.tree.isofor.IsolationForestModel.IsolationForestParameters;

/**
 * Mojo definition for Extended Isolation Forest model.
 */
public class ExtendedIsolationForestMojoWriter extends SharedTreeMojoWriter<ExtendedIsolationForestModel, ExtendedIsolationForestModel.ExtendedIsolationForestParameters, ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public ExtendedIsolationForestMojoWriter() {}

  public ExtendedIsolationForestMojoWriter(ExtendedIsolationForestModel model) { super(model); }

  @Override 
  public String mojoVersion() {
    return "1.0";
  }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
//    writekv("test", model._output.test);
//    writekv("min_path_length", model._output._min_path_length);
  }

}
