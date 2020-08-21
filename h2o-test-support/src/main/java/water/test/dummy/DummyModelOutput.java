package water.test.dummy;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.fvec.Frame;

public class DummyModelOutput extends Model.Output {
  public final String _msg;
  public DummyModelOutput(ModelBuilder b, Frame train, String msg) {
    super(b, train);
    _msg = msg;
  }
  @Override
  public ModelCategory getModelCategory() {
    return ModelCategory.Binomial;
  }
  @Override
  public boolean isSupervised() {
    return true;
  }
}
