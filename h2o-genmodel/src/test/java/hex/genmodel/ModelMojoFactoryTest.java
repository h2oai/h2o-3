package hex.genmodel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ModelMojoFactoryTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void getUnknownMojoReader() throws Exception {
    thrown.expectMessage("Algorithm `Unknown` is not supported by this version of h2o-genmodel. " +
            "If you are using an algorithm implemented in an extension, be sure to include a jar dependency of the extension (eg.: ai.h2o:h2o-genmodel-ext-unknown)");
    ModelMojoFactory.INSTANCE.getMojoReader("Unknown");
  }

}