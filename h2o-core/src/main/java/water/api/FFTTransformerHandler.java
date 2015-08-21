package water.api;

import water.util.FFTTransformer;

public class FFTTransformerHandler extends Handler {

  public FFTTransformerV3 run(int version, FFTTransformerV3 sf) {
    FFTTransformer fft = sf.createAndFillImpl();
    return (FFTTransformerV3) Schema.schema(version, FFTTransformer.class).fillFromImpl(fft.exec());
  }
}