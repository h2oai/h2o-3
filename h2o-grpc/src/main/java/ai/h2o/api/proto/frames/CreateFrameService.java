package ai.h2o.api.proto.frames;


import ai.h2o.api.GrpcUtils;
import ai.h2o.api.proto.core.JobInfo;
import ai.h2o.api.proto.core.JobService;
import hex.createframe.recipes.SimpleCreateFrameRecipe;
import io.grpc.stub.StreamObserver;
import water.Job;
import water.Key;
import water.fvec.Frame;

/**
 */
public class CreateFrameService extends CreateFrameGrpc.CreateFrameImplBase {

  @Override
  public void simple(CreateFrameSimpleSpec request, StreamObserver<JobInfo> responseObserver) {
    try {
      SimpleCreateFrameRecipe cf = new SimpleCreateFrameRecipe();
      cf.dest = Key.make(request.getTargetId());
      cf.seed = request.getSeed();
      cf.nrows = request.getNrows();
      cf.ncols_real = request.getNcolsReal();
      cf.ncols_int = request.getNcolsInt();
      cf.ncols_enum = request.getNcolsEnum();
      cf.ncols_bool = request.getNcolsBool();
      cf.ncols_str = request.getNcolsStr();
      cf.ncols_time = request.getNcolsTime();
      cf.real_lb = request.getRealLb();
      cf.real_ub = request.getRealUb();
      cf.int_lb = request.getIntLb();
      cf.int_ub = request.getIntUb();
      cf.enum_nlevels = request.getEnumNlevels();
      cf.bool_p = request.getBoolP();
      cf.time_lb = request.getTimeLb();
      cf.time_ub = request.getTimeUb();
      cf.str_length = request.getStrLength();
      cf.missing_fraction = request.getMissingFraction();
      cf.response_type = SimpleCreateFrameRecipe.ResponseType.valueOf(request.getResponseType().toString());
      cf.response_lb = request.getResponseLb();
      cf.response_ub = request.getResponseUb();
      cf.response_p = request.getResponseP();
      cf.response_nlevels = request.getResponseNlevels();

      Job<Frame> job = cf.exec();
      responseObserver.onNext(JobService.fillJobInfo(job));
      responseObserver.onCompleted();

    } catch (Throwable ex) {
      GrpcUtils.sendError(ex, responseObserver, JobInfo.class);
    }
  }

}
