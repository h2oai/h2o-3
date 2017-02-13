package ai.h2o.api.proto.core;

import io.grpc.stub.StreamObserver;
import water.*;

import static ai.h2o.api.proto.core.JobInfo.Status.*;


/**
 */
public class JobService extends JobGrpc.JobImplBase {

  @Override
  public void poll(JobId request, StreamObserver<JobInfo> responseObserver) {
    try {
      water.Job job = resolveJob(request);
      responseObserver.onNext(fillJobInfo(job));
      responseObserver.onCompleted();
    } catch (Throwable ex) {
      GrpcCommon.sendError(ex, responseObserver, JobInfo.class);
    }
  }

  @Override
  public void cancel(JobId request, StreamObserver<JobInfo> responseObserver) {
    try {
      water.Job job = resolveJob(request);
      job.stop();
      responseObserver.onNext(fillJobInfo(job));
      responseObserver.onCompleted();
    } catch (Throwable ex) {
      GrpcCommon.sendError(ex, responseObserver, JobInfo.class);
    }
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Helpers
  //--------------------------------------------------------------------------------------------------------------------

  private static water.Job resolveJob(JobId request) {
    String strId = request.getJobId();
    Value val = DKV.get(Key.make(strId));
    if (val == null) {
      throw new IllegalArgumentException("Job " + strId + " not found in the DKV");
    }
    Iced iced = val.get();
    if (iced instanceof Job) {
      return (water.Job) iced;
    } else {
      throw new IllegalArgumentException("Id " + strId + " does not reference a Job but a " + iced.getClass());
    }
  }


  public static JobInfo fillJobInfo(water.Job job) {
    JobInfo.Builder jb = JobInfo.newBuilder();
    jb.setJobId(job._key.toString())
      .setProgress(job.progress())
      .setMessage(job.progress_msg())
      .setDuration(job.msec());

    if (job.isRunning()) {
      jb.setStatus(job.stop_requested()? STOPPING : RUNNING);
    } else {
      jb.setStatus(job.stop_requested()? CANCELLED : DONE);
    }

    Throwable ex = job.ex();
    if (ex != null) {
      jb.setStatus(FAILED)
        .setError(GrpcCommon.buildError(ex, 0));
    }

    if (job._result != null && !job.readyForView())
      jb.setTargetId(job._result.toString());

    String ttype = TypeMap.theFreezable(job._typeid).getClass().getSimpleName();
    jb.setTargetType(JobInfo.TargetType.valueOf(ttype));

    return jb.build();
  }
}
