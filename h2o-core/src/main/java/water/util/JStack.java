package water.util;

import water.Iced;
import water.H2O;

public class JStack extends Iced {
  public JStackCollectorTask.DStackTrace _traces[];

  public JStack execImpl() {
    _traces = new JStackCollectorTask().doAllNodes()._traces;
    return this;                // flow coding
  }
}
