package hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Job;
import water.Scope;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.assertEquals;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelBuilderHelperTest  {

  @Test
  public void trainModelsParallel() {
    try {
      Scope.enter();
      DummyModelBuilder[] dm = new DummyModelBuilder[]{
              new DummyModelBuilder(new DummyModelParameters()),
              new DummyModelBuilder(new DummyModelParameters())
      };
      DummyModelBuilder[] result = ModelBuilderHelper.trainModelsParallel(dm, 2);
      assertEquals(2, result.length);
      for (DummyModelBuilder dmb : result)
        assertEquals(Job.JobStatus.SUCCEEDED, dmb._job.getStatus());
    } finally {
      Scope.exit();
    }
  }

}
