package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import water.fvec.Frame;
import hex.encoding.CategoricalEncoder;
import hex.encoding.CategoricalEncoderProvider;
import hex.encoding.CategoricalEncoding;
import hex.encoding.CategoricalEncodingSupport;

public class TargetEncoderCategoricalEncoderProvider implements CategoricalEncoderProvider {
  @Override
  public String getScheme() {
    return CategoricalEncoding.Scheme.TargetEncoding.name();
  }

  @Override
  public CategoricalEncoder getEncoder(CategoricalEncodingSupport params) {
    // store the instance in params to avoid retraining? How? add getEncoder/setEncoder to the interface?
    return new TargetEncoderAsCategoricalEncoder(params);
  }
  
  
  static class TargetEncoderAsCategoricalEncoder implements CategoricalEncoder {
    
    private TargetEncoderModel _teModel;

    public TargetEncoderAsCategoricalEncoder() { this(null);}
    
    public TargetEncoderAsCategoricalEncoder(CategoricalEncodingSupport params) {
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      //TODO: obtain the training frame!!!
      TargetEncoder te = new TargetEncoder(teParams);
      _teModel = te.trainModel().get();
    }

    @Override
    public Frame encode(Frame fr, String[] skipCols) {
      Frame toEncode = new Frame(fr);
      
      toEncode.remove(skipCols);
      Frame encoded = _teModel.transform(toEncode);
      
      return encoded;
    }
  }
}

