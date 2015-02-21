package hex.glm;

import hex.glm.GLMModel.GLMParameters;
import water.MRTask;
import water.fvec.Chunk;

///**
// * Created by tomasnykodym on 9/12/14.
// */
//public class GLMValidationTsk extends MRTask<GLMValidationTsk> {
//  final GLMParameters _params;
//  final double _ymu;
//  final int _rank;
//  GLMValidation _val;
//
//  public GLMValidationTsk(GLMParameters params, double ymu, int rank){
//    _params = params;
//    _ymu = ymu;
//    _rank = rank;
//  }
//  @Override
//  public void map(Chunk actual, Chunk predict){
//    GLMValidation val = new GLMValidation(null,_ymu,_params,_rank);
//    for(int i = 0; i < actual._len; ++i) {
//      double predicted = predict.atd(i);
//      double real = actual.atd(i);
//      if(!Double.isNaN(real) && !Double.isNaN(predicted))
//        val.add(real, predicted);
//    }
//    _val = val;
//  }
//  @Override
//  public void reduce(GLMValidationTsk gmt){ _val.add(gmt._val); }
//}
