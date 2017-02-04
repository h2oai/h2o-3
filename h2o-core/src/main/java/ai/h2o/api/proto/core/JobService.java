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
    Key<water.Job> key = Key.make(request.getId());
    Value val = DKV.get(key);
    if (val == null)
      throw new IllegalArgumentException("Job " + request.getId() + " is missing");
    Iced iced = val.get();
    if (!(iced instanceof water.Job))
      throw new IllegalArgumentException("Id " + request.getId() + " references a " + iced.getClass() + " not a Job");

    water.Job job = (water.Job) iced;
    responseObserver.onNext(collectJobInfo(job));
    responseObserver.onCompleted();
  }

  @Override
  public void cancel(JobId request, StreamObserver<JobInfo> responseObserver) {
    Key<water.Job> key = Key.make(request.getId());
    Job job = DKV.getGet(key);
    if (job == null) {
      throw new IllegalArgumentException("No job with key " + key);
    }
    job.stop(); // Request Job stop

    responseObserver.onNext(collectJobInfo(job));
    responseObserver.onCompleted();
  }


  private JobInfo collectJobInfo(water.Job job) {
    JobInfo.Builder jb = JobInfo.newBuilder();
    jb.setId(job._key.toString())
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
