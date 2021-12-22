package hex.mojo;

import hex.genmodel.MojoModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GbmMojoWriter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class MojoIntegrationTest {

  @Test
  public void testMojo_MultilineCategoricals() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = new TestFrameBuilder().withDataForCol(0, new String[]{"L1\n \"\"L1", "L2\n\r", "\nL3\n", "L4\r", "\\", "L6\f", "\rL7\b"})
              .withDataForCol(1, new String[]{"\"R1", "R2", "R3", "", "   ", "R6\t", "R7\b"})
              .withUniformVecTypes(2, Vec.T_CAT)
              .build();

      Scope.track(trainingFrame);

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._response_column = trainingFrame._names[trainingFrame.numCols()-1];
      parameters._seed = 0XFEED;
      parameters._max_depth = 1;
      parameters._ntrees = 1;
      parameters._min_rows = 1;
      
      
      GBM gbm = new GBM(parameters);
      final GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);
      assertNotNull(gbmModel);

      final File originalModelMojoFile = File.createTempFile("mojo", "zip");
      gbmModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

      MojoModel mojoModel = MojoModel.load(originalModelMojoFile.getAbsolutePath());
      assertNotNull(mojoModel);
      
      for (int i = 0; i < gbmModel._output._domains.length; i++) {
        assertArrayEquals(gbmModel._output._domains[i], mojoModel._domains[i]);
      }


    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojo_parseUnquoted() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = new TestFrameBuilder().withDataForCol(0, new String[]{"LVL1", " LVL2  ", "LVL 3 ", " LVL4 "}) // Third with space in the middle
              .withDataForCol(1, new String[]{"RLVL1", "RLVL2", "", "RLVL4"}).
                      withUniformVecTypes(2, Vec.T_CAT)
              .build();

      Scope.track(trainingFrame);

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._response_column = trainingFrame._names[trainingFrame.numCols() - 1];
      parameters._seed = 0XFEED;
      parameters._max_depth = 1;
      parameters._ntrees = 1;
      parameters._min_rows = 1;


      GBM gbm = new GBM(parameters);
      final GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);

      assertNotNull(gbmModel);

      final File originalModelMojoFile = File.createTempFile("mojo", "zip");
      new OldDomainSerializationGBMMojoWriter(gbmModel).writeTo(new FileOutputStream(originalModelMojoFile));


      MojoModel mojoModel = MojoModel.load(originalModelMojoFile.getAbsolutePath());
      assertNotNull(mojoModel);

      for (int i = 0; i < gbmModel._output._domains.length; i++) {
        assertArrayEquals(gbmModel._output._domains[i], mojoModel._domains[i]);
      }
    } finally {
      Scope.exit();
    }
  }

  /**
   * Custom Mojo writer with categorical levels serialized "the old way" - one categorical per row.
   * This approach made it impossible to represent multi-level categoricals.
   * 
   * No need to override `writekv("multiline_categoricals", true);` to false, as the DFA should be able to recognize a line not beginning with a quote.
   */
  private static final class OldDomainSerializationGBMMojoWriter extends GbmMojoWriter {

    public OldDomainSerializationGBMMojoWriter(GBMModel model) {
      super(model);
    }

    /**
     * Provide categorical levels output "the old way"
     *
     * @throws IOException
     */
    @Override
    protected void writeDomains() throws IOException {
      int domIndex = 0;
      for (String[] domain : model._output._domains) {
        if (domain == null) continue;
        startWritingTextFile(String.format("domains/d%03d.txt", domIndex++));
        for (String category : domain) {
          writeln(category.replaceAll("\n", "\\n"));  // replace newlines with "\n" escape sequences
        }
        finishWritingTextFile();
      }
    }
  }


}
