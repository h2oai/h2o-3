package ai.h2o.automl.targetencoding;

import water.Key;
import water.MRTask;
import water.fvec.Vec;

public class UpdateVecDomainTask extends MRTask<UpdateVecDomainTask> {

  private final Key<Vec> _key;
  private final String[] _domain;

  public UpdateVecDomainTask(Key<Vec> key, String[] domain) {
    _key = key;
    _domain = domain;
  }

  @Override
  protected void setupLocal() {
    final Vec vec = _key.get();
    vec.setDomain(_domain);
  }
}
