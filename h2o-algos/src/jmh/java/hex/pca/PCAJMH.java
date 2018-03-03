package hex.pca;

import hex.DataInfo;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.FrameUtils;

import static hex.pca.JMHConfiguration.logLevel;
import static hex.pca.PCAModel.PCAParameters.Method.GramSVD;
import static water.TestUtil.parse_test_file;
import static water.TestUtil.stall_till_cloudsize;

public class PCAJMH {
  
  PCAModel.PCAParameters paramsQuasar;
  protected PCAModel pcaModel;
  protected Frame trainingFrame;
  protected String hexKey = "input_data.hex";
  protected String dataSetFilePath = "smalldata/pca_test/SDSS_quasar.txt";
//	protected String dataSetFilePath = "bigdata/laptop/jira/re0.wc.arff.csv";
  
  public void setup() {
    water.util.Log.setLogLevel(logLevel);
    stall_till_cloudsize(1);
    
    trainingFrame = null;
    double missing_fraction = 0.75;
    long seed = 12345;
    
    try {
      // TODO update following comment
    /* NOTE get the data this way
     * 1) ./gradlew syncSmalldata
     * 2) unzip SDSS_quasar.txt.zip
     */
      trainingFrame = parse_test_file(Key.make(hexKey), dataSetFilePath);
      // Add missing values to the training data
      Frame frame = new Frame(Key.<Frame>make(), trainingFrame.names(), trainingFrame.vecs());
      DKV.put(frame._key, frame); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
      FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frame._key, seed, missing_fraction);
      j.execImpl().get(); // MissingInserter is non-blocking, must block here explicitly
      DKV.remove(frame._key); // Delete the frame header (not the data)
      
      paramsQuasar = new PCAModel.PCAParameters();
      paramsQuasar._train = trainingFrame._key;
      paramsQuasar._k = 4;
      paramsQuasar._transform = DataInfo.TransformType.NONE;
      paramsQuasar._pca_method = GramSVD;
      paramsQuasar._impute_missing = true;   // Don't skip rows with NA entries, but impute using mean of column
      paramsQuasar._seed = seed;
    } catch (RuntimeException e) {
      if (trainingFrame != null) {
        trainingFrame.delete();
      }
      e.printStackTrace();
      throw e;
    }
  }
  
  public void tearDown() {
    if (pcaModel != null) {
      pcaModel.remove();
    }
    if (trainingFrame != null) {
      trainingFrame.delete();
    }
  }
  
  boolean tryToTrain() {
    try {
      pcaModel = new PCA(paramsQuasar).trainModel().get();
    } catch (Exception exception) {
      return false;
    }
    return true;
  }
  
  boolean tryToScore() {
    try {
      pcaModel.score(trainingFrame);
    } catch (Exception e) {
      return false;
    }
    return true;
  }
  
}
