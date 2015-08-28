package water.api;

import water.util.DCTTransformer;

public class DCTTransformerHandler extends Handler {

  public DCTTransformerV3 run(int version, DCTTransformerV3 sf) {
    DCTTransformer fft = sf.createAndFillImpl();
    return (DCTTransformerV3) Schema.schema(version, DCTTransformer.class).fillFromImpl(fft.exec());
  }
}