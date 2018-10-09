package hex.tree.isofor;

import hex.tree.SharedTreeMojoWriter;

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
    return "1.00";
  }

}
