package ai.h2o.api.proto.core;

import ai.h2o.api.GrpcUtils;
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
      String jobId = request.getJobId();
      GrpcUtils.sendError(ex, responseObserver, JobInfo.newBuilder().setJobId(jobId).setStatus(FAILED));
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
      String jobId = request.getJobId();
      GrpcUtils.sendError(ex, responseObserver, JobInfo.newBuilder().setJobId(jobId).setStatus(FAILED));
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
    float progress = job.progress();

    JobInfo.Builder jb = JobInfo.newBuilder();
    jb.setJobId(job._key.toString());
    jb.setDuration(job.msec());

    String message = job.progress_msg();
    if (message != null)
      jb.setMessage(message);

    if (job.isRunning()) {
      jb.setStatus(job.stop_requested()? STOPPING : RUNNING);
    } else {
      jb.setStatus(job.stop_requested()? CANCELLED : DONE);
    }
    if (jb.getStatus() == RUNNING && progress >= 1) progress = 0.999f;
    if (jb.getStatus() == DONE && progress < 1) progress = 1;
    jb.setProgress(progress);

    Throwable ex = job.ex();
    if (ex != null) {
      jb.setStatus(FAILED)
        .setError(GrpcUtils.buildError(ex, 0));
    }

    if (job._result != null && !job.readyForView())
      jb.setTargetId(job._result.toString());

    String ttype = TypeMap.theFreezable(job._typeid).getClass().getSimpleName();
    jb.setTargetType(JobInfo.TargetType.valueOf(ttype.toUpperCase()));

    return jb.build();
  }
}
