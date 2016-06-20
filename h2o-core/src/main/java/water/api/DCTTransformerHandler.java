package water.api;

import water.Job;
import water.util.DCTTransformer;

public class DCTTransformerHandler extends Handler {

  public JobV3 run(int version, DCTTransformerV3 sf) {
    DCTTransformer fft = sf.createAndFillImpl();
    return (JobV3) SchemaServer.schema(version, Job.class).fillFromImpl(fft.exec());
  }
}