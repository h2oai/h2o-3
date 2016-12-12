package hex.word2vec;

import hex.DataInfo;
import water.DKV;
import water.IcedUtils;
import water.fvec.Frame;
import water.fvec.InteractionWrappedVec;
import water.fvec.Vec;

/**
 * Created by vpatryshev on 12/11/16.
 */
public class DataInfoExtras {
  public static void addResponse(DataInfo dataInfo, String [] names, Vec[] vecs) {
    dataInfo._adaptedFrame.add(names,vecs);
    dataInfo._numResponses += vecs.length;
  }

  public static DataInfo scoringInfo(DataInfo dataInfo, Frame adaptFrame){
    DataInfo res = IcedUtils.deepCopy(dataInfo);
    res._normMul = null;
    res._normRespSub = null;
    res._normRespMul = null;
    res._normRespSub = null;
    res._predictor_transform = DataInfo.TransformType.NONE;
    res._response_transform = DataInfo.TransformType.NONE;
    res._adaptedFrame = adaptFrame;
    res._weights = dataInfo._weights && adaptFrame.find(dataInfo._adaptedFrame.name(dataInfo.weightChunkId())) != -1;
    res._offset = dataInfo._offset && adaptFrame.find(dataInfo._adaptedFrame.name(dataInfo.offsetChunkId())) != -1;
    res._fold = dataInfo._fold && adaptFrame.find(dataInfo._adaptedFrame.name(dataInfo.foldChunkId())) != -1;
    int resId = adaptFrame.find((dataInfo._adaptedFrame.name(dataInfo.responseChunkId(0))));
    if(resId == -1 || adaptFrame.vec(resId).isBad())
      res._numResponses = 0;
    else {// NOTE: DataInfo can have extra columns encoded as response, e.g. helper columns when doing Multinomail IRLSM, don't need those for scoring!.
      res._numResponses = 1;
    }
    res._valid = true;
    res._interactions=dataInfo._interactions;
    res._interactionColumns=dataInfo._interactionColumns;

    // ensure that vecs are in the DKV, may have been swept up in the Scope.exit call
    for( Vec v: res._adaptedFrame.vecs() )
      if( v instanceof InteractionWrappedVec) {
        ((InteractionWrappedVec)v)._useAllFactorLevels=dataInfo._useAllFactorLevels;
        ((InteractionWrappedVec)v)._skipMissing=dataInfo._skipMissing;
        DKV.put(v);
      }
    return res;
  }

  public static Vec setWeights(DataInfo di, String name, Vec vec) {
    if (vec == null) return null;
    if(di._weights)
      return di._adaptedFrame.replace(di.weightChunkId(),vec);
    di._adaptedFrame.insertVec(di.weightChunkId(),name,vec);
    di._weights = true;
    return null;
  }

  public static void dropWeights(DataInfo di) {
    if(di._weights) {
      di._adaptedFrame.remove(di.weightChunkId());
      di._weights = false;
    }
  }
}
