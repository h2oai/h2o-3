package hex.genmodel.easy;

import hex.genmodel.GenModel;


public class EnumLimitedEncoderColumnMapper extends EnumEncoderColumnMapper {

  public EnumLimitedEncoderColumnMapper(GenModel m) {
    super(m);
  }
  
  @Override
  public String[] getModelColumnNames() {
    return _m.getOrigNames();
  }
}
