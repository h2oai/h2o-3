package water.api;

import water.api.schemas3.DCTTransformerV3;
import water.api.schemas3.JobV3;
import water.util.DCTTransformer;

public class DCTTransformerHandler extends Handler {

  public JobV3 run(int version, DCTTransformerV3 sf) {
    DCTTransformer fft = sf.createAndFillImpl();
    return new JobV3(fft.exec());
  }
}