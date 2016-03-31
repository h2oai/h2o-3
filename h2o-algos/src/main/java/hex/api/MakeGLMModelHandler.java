package hex.api;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import hex.schemas.DataInfoFrameV3;
import hex.schemas.GLMModelV3;
import hex.schemas.MakeGLMModelV3;
import water.DKV;
import water.Key;
import water.MRTask;
import water.api.Handler;
import water.api.KeyV3;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;
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
    GLMModel m = new GLMModel(args.dest != null?args.dest.key():Key.make(),model._parms,null, new double[]{.5}, Double.NaN, Double.NaN, -1);
    DataInfo dinfo = model.dinfo();
    dinfo.setPredictorTransform(TransformType.NONE);
    // GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
    m._output = new GLMOutput(model.dinfo(),model._output._names, model._output._domains, model._output.coefficientNames(), model._output._binomial, beta);
    DKV.put(m._key, m);
    GLMModelV3 res = new GLMModelV3();
    res.fillFromImpl(m);
    return res;
  }

  // instead of adding a new endpoint, just put this stupid test functionality here
 /** Get the expanded (interactions + offsets) dataset. Careful printing! Test only
  */

  public DataInfoFrameV3 getDataInfoFrame(int version, DataInfoFrameV3 args) {
    Frame fr = DKV.getGet(args.frame.key());
    if( null==fr ) throw new IllegalArgumentException("no frame found");
    args.result = new KeyV3.FrameKeyV3(HoTDAAWWG(fr,args.interactions,args.useAll,args.standardize)._key);
    return args;
  }

  public Frame HoTDAAWWG(Frame fr, String[] interactions, boolean useAll, boolean standardize) {
    final DataInfo dinfo = new DataInfo(fr,null,1,useAll,standardize?TransformType.STANDARDIZE:TransformType.NONE,TransformType.NONE,true,false,false,false,false,false,interactions);
    byte[] types = new byte[dinfo.fullN()];
    Arrays.fill(types, Vec.T_NUM);
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk ncs[]) {
        DataInfo.Row r = dinfo.newDenseRow();
        for(int i=0;i<cs[0]._len;++i) {
          r=dinfo.extractDenseRow(cs,i,r);
          for(int n=0;n<ncs.length;++n)
            ncs[n].addNum(r.get(n));
        }
      }
    }.doAll(types,dinfo._adaptedFrame.vecs()).outputFrame(Key.make(), dinfo.coefNames(), null);
  }
}
