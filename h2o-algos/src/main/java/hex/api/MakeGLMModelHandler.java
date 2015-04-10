package hex.api;

import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import hex.schemas.GLMModelV2;
import hex.schemas.MakeGLMModelV2;
import water.DKV;
import water.api.Handler;

import java.util.Map;

/**
 * Created by tomasnykodym on 3/25/15.
 */
public class MakeGLMModelHandler extends Handler {
  public GLMModelV2 make_model(int version, MakeGLMModelV2 args){
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
    // beta has new coefficients in proper order
    System.out.println("coefs:");
    System.out.println(coefs);
    GLMModel m = new GLMModel(args.dest.key(),model._parms,new GLMOutput(model._output._names,model._output._domains, names, beta,.5f,model._output._binomial), model.dinfo(), Double.NaN, Double.NaN, -1);
    DKV.put(m._key, m);
    GLMModelV2 res = new GLMModelV2();
    res.fillFromImpl(m);
    return res;
  }

}
