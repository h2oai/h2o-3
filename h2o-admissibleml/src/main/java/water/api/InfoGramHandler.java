package water.api;

import water.api.schemas.InfoGramV99;

import java.util.Properties;

public class InfoGramHandler extends Handler{
  
  @Override
  public InfoGramV99 handle(int version, water.api.Route route, Properties parms, String postBody) throws Exception {
    final String methodName = route._handler_method.getName();
    if ("train".equals(methodName)) {
      return trainInfoGram(parms);
    } else {
      throw water.H2O.unimpl();
    }
  }

  private InfoGramV99 trainInfoGram(Properties parms) {
    InfoGramV99 infogramS = new InfoGramV99();
    return infogramS;
  }
}
