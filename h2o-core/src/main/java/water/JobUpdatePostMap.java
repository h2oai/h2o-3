package water;

import water.fvec.Chunk;

public class JobUpdatePostMap extends MRTask.PostMapAction<JobUpdatePostMap> {
  private final Job<?> _job;

  private JobUpdatePostMap(Job<?> _job) {
    this._job = _job;
  }

  @Override
  void call(Key mapInput) {
    _job.update(1);
  }

  @Override
  void call(Chunk[] mapInput) {
    _job.update(mapInput[0].len());
  }

  public static JobUpdatePostMap forJob(Job<?> job) {
    return job != null ? new JobUpdatePostMap(job) : null;
  }
}
