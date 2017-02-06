package ai.h2o.api.proto.core;

import ai.h2o.api.protos.core.JobGrpc;
import ai.h2o.api.protos.core.JobId;
import ai.h2o.api.protos.core.JobInfo;
import io.grpc.stub.StreamObserver;
import water.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import static ai.h2o.api.protos.core.JobInfo.Status.*;


/**
 */
public class JobService extends JobGrpc.JobImplBase {

  @Override
  public void poll(JobId request, StreamObserver<JobInfo> responseObserver) {
    water.Job job = resolveJob(request);
    responseObserver.onNext(fillJobInfo(job));
    responseObserver.onCompleted();
  }

  @Override
  public void cancel(JobId request, StreamObserver<JobInfo> responseObserver) {
    water.Job job = resolveJob(request);
    job.stop();
    responseObserver.onNext(fillJobInfo(job));
    responseObserver.onCompleted();
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  private water.Job resolveJob(JobId request) {
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


  private JobInfo fillJobInfo(water.Job job) {
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
      StringWriter sw = new StringWriter();
      ex.printStackTrace(new PrintWriter(sw));
      jb.setStatus(FAILED)
        .setException(ex.toString())
        .setStacktrace(sw.toString());
    }

    if (job._result != null && !job.readyForView())
      jb.setTargetId(job._result.toString());

    String ttype = TypeMap.theFreezable(job._typeid).getClass().getSimpleName();
    jb.setTargetType(JobInfo.TargetType.valueOf(ttype));

    return jb.build();
  }
}
