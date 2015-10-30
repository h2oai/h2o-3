package hex.api;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import hex.schemas.GLMModelV3;
import hex.schemas.MakeGLMModelV3;
import water.DKV;
import water.Key;
import water.api.Handler;

import java.util.Map;

/**
 * Created by tomasnykodym on 3/25/15.
 */
public class MakeGLMModelHandler extends Handler {
  public GLMModelV3 make_model(int version, MakeGLMModelV3 args){
    GLMModel model = DKV.getGet(args.model.key());
    if(model == null)
      throw new IllegalArgumentException("missing source model " + args.model);
    String [] names = model._output.coefficientNames();
    Map<String,Double> coefs = model.coefficients();
    for(int i = 0; i < args.names.length; ++i)
      coefs.put(args.names[i],args.beta[i]);
    double [] beta = model.beta().clone();
    for(int i = 0; i < beta.length; ++i)
      beta[i] = coefs.get(names[i]);
    GLMModel m = new GLMModel(args.dest != null?args.dest.key():Key.make(),model._parms,null, new double[]{.5}, Double.NaN, Double.NaN, -1, false, false);
    DataInfo dinfo = model.dinfo();
    dinfo.setPredictorTransform(TransformType.NONE);
    // GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
    m._output = new GLMOutput(model.dinfo(),model._output._names, model._output._domains, model._output.coefficientNames(), model._output._binomial, beta);
    DKV.put(m._key, m);
    GLMModelV3 res = new GLMModelV3();
    res.fillFromImpl(m);
    return res;
  }

}
