package hex.api;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import hex.gram.Gram;
import hex.operations.OneHot;
import hex.schemas.*;
import water.DKV;
import water.Key;
import water.api.schemas3.KeyV3;
import water.fvec.*;

import java.util.Map;
import water.fvec.Vec.VectorGroup;
import water.http.handlers.Handler;

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
    GLMModel m = new GLMModel(args.dest != null?args.dest.key():Key.make(),model._parms,null, model._ymu, Double.NaN, Double.NaN, -1);
    DataInfo dinfo = model.dinfo();
    dinfo.setPredictorTransform(TransformType.NONE);
    // GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
    m._output = new GLMOutput(model.dinfo(),model._output._names, model._output._domains, model._output.coefficientNames(), model._output._binomial, beta);
    DKV.put(m._key, m);
    GLMModelV3 res = new GLMModelV3();
    res.fillFromImpl(m);
    return res;
  }

  public GLMRegularizationPathV3 extractRegularizationPath(int v, GLMRegularizationPathV3 args) {
    GLMModel model = DKV.getGet(args.model.key());
    if(model == null)
      throw new IllegalArgumentException("missing source model " + args.model);
    return new GLMRegularizationPathV3().fillFromImpl(model.getRegularizationPath());
  }
  // instead of adding a new endpoint, just put this stupid test functionality here
 /** Get the expanded (interactions + offsets) dataset. Careful printing! Test only
  */

  public DataInfoFrameV3 getDataInfoFrame(int version, DataInfoFrameV3 args) {
    Frame fr = DKV.getGet(args.frame.key());
    if( null==fr ) throw new IllegalArgumentException("no frame found");
    args.result = new KeyV3.FrameKeyV3(OneHot.oneHot(fr, args.interactions, args.use_all, args.standardize, args.interactions_only, true)._key);
    return args;
  }

  public GramV3 computeGram(int v, GramV3 input){
    if(DKV.get(input.X.key()) == null)
      throw new IllegalArgumentException("Frame " + input.X.key() + " does not exist.");
    Frame fr = input.X.key().get();
    Frame frcpy = new Frame(fr._names.clone(),fr.vecs().clone());
    String wname = null;
    Vec weight = null;
    if(input.W != null && !input.W.column_name.isEmpty()) {
      wname = input.W.column_name;
      if(fr.find(wname) == -1) throw new IllegalArgumentException("Did not find weight vector " + wname);
      weight = frcpy.remove(wname);
    }
    DataInfo dinfo = new DataInfo(frcpy,null,0,input.use_all_factor_levels,input.standardize?TransformType.STANDARDIZE:TransformType.NONE,TransformType.NONE,input.skip_missing,false,!input.skip_missing,/* weight */ false, /* offset */ false, /* fold */ false, /* intercept */ true);
    DKV.put(dinfo);
    if(weight != null)dinfo.setWeights(wname,weight);
    Gram.GramTask gt = new Gram.GramTask(null,dinfo,false,true).doAll(dinfo._adaptedFrame);
    double [][] gram = gt._gram.getXX();
    dinfo.remove();
    String [] names = water.util.ArrayUtils.append(dinfo.coefNames(),"Intercept");
    Vec [] vecs = new Vec[gram.length];
    Key[] keys = new VectorGroup().addVecs(vecs.length);
    for(int i = 0; i < vecs.length; ++i)
      vecs[i] = Vec.makeVec(gram[i],keys[i]);
    input.destination_frame = new KeyV3.FrameKeyV3();
    String keyname = input.X.key().toString();
    if(keyname.endsWith(".hex"))
      keyname = keyname.substring(0,keyname.lastIndexOf("."));
    keyname = keyname + "_gram";
    if(weight != null)
      keyname = keyname + "_" + wname;
    Key k = Key.make(keyname);
    if(DKV.get(k) != null){
      int cnt = 0;
      while(cnt < 1000 && DKV.get(k = Key.make(keyname + "_" + cnt)) != null)cnt++;
      if(cnt == 1000) throw new IllegalArgumentException("unable to make unique key");
    }
    input.destination_frame.fillFromImpl(k);
    DKV.put(new Frame(k, names,vecs));
    return input;
  }
}
