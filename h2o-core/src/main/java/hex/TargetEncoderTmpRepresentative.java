package hex;

import water.fvec.Frame;

import java.util.Map;

public class TargetEncoderTmpRepresentative extends FeatureTransformer {

  Map<String, Frame> targetEncodingMap;

  @Override
  protected FeatureTransformerWriter getWriter() {
    return new TEFeatureTransformerWriter(this);
  }


  public void setTargetEncodingMap(Map<String, Frame> targetEncodingMap) {
    this.targetEncodingMap = targetEncodingMap;
  }

  public Map<String, Frame> getTargetEncodingMap() {
    return targetEncodingMap;
  }
}
