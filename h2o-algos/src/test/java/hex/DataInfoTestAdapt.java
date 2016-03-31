package hex;

import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;
// data info tests with interactions


public class DataInfoTestAdapt extends TestUtil {

  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }





  @Test public void testInteractionTrainTestSplitAdapt() {
    DataInfo dinfo=null, scoreInfo=null;
    Frame fr=null, expanded=null;
    Frame[] frSplits=null, expandSplits=null;
    String[] interactions = new String[]{"class", "sepal_len"};

    boolean useAll=false;
    boolean standardize=false;  // golden frame is standardized before splitting, while frame we want to check would be standardized post-split (not exactly what we want!)

    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
      fr.swap(3, 4);
      expanded = GLMModel.GLMOutput.expand(fr, interactions, useAll, standardize);   // here's the "golden" frame

      // now split fr and expanded
      long seed;
      frSplits = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed = new Random().nextLong());
      expandSplits = ShuffleSplitFrame.shuffleSplitFrame(expanded, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed);

      // check1: verify splits. expand frSplits with DataInfo and check against expandSplits
      checkSplits(frSplits,expandSplits,interactions,useAll,standardize);

      // now take the test frame from frSplits, and adapt it to a DataInfo built on the train frame
      dinfo = makeInfo(frSplits[0], interactions, useAll, standardize);
      Model.adaptTestForTrain(dinfo._adaptedFrame.names(),null,null,null,"petal_wid",dinfo._adaptedFrame.domains(),frSplits[1],Double.NaN,true,false,interactions);

      scoreInfo = dinfo.scoringInfo(frSplits[1]);
      checkFrame(scoreInfo,expandSplits[1]);

    } finally {
      cleanup(fr,expanded);
      cleanup(frSplits);
      cleanup(expandSplits);
      cleanup(dinfo, scoreInfo);
    }
  }

  private void cleanup(Frame... fr) {
    for(Frame f: fr) if( null!=f ) f.delete();
  }

  private void cleanup(DataInfo... di) {
    for(DataInfo d: di) if( null!=d ) {
      d.dropInteractions();
      d.remove();
    }
  }


  private void checkSplits(Frame frSplits[], Frame goldSplits[], String[] interactions, boolean useAll, boolean standardize) {
    for(int i=0;i<frSplits.length;++i)
      checkFrame(makeInfo(frSplits[i],interactions,useAll,standardize),goldSplits[i]);
  }

  private static DataInfo makeInfo(Frame fr, String[] interactions, boolean useAll, boolean standardize) {
    return new DataInfo(
            fr,          // train
            null,        // valid
            1,           // num responses
            useAll,        // use all factor levels
            standardize?DataInfo.TransformType.STANDARDIZE:DataInfo.TransformType.NONE,  // predictor transform
            DataInfo.TransformType.NONE,  // response  transform
            true,        // skip missing
            false,       // impute missing
            false,       // missing bucket
            false,       // weight
            false,       // offset
            false,       // fold
            interactions  // interactions
    );
  }

  private void checkFrame(final Frame checkMe, final Frame gold) {
    Vec[] vecs = new Vec[checkMe.numCols()+gold.numCols()];
    new MRTask() {
      @Override public void map(Chunk[] cs) {
        int off=checkMe.numCols();
        for(int i=0;i<off;++i) {
          for(int r=0;r<cs[0]._len;++r) {
            double check = cs[i].atd(r);
            double gold  = cs[i+off].atd(r);
            if( Math.abs(check-gold) > 1e-12 )
              throw new RuntimeException("bonk");
          }
        }
      }
    }.doAll(vecs);
  }

  private void checkFrame(final DataInfo di, final Frame gold) {
    try {
      new MRTask() {
        @Override
        public void map(Chunk[] cs) {
          DataInfo.Row r = di.newDenseRow();
          for (int i = 0; i < cs[0]._len; ++i) {
            di.extractDenseRow(cs, i, r);
            for (int j = 0; j < di.fullN(); ++j) {
              double goldValue = gold.vec(j).at(i + cs[0].start());
              double thisValue = r.get(j); // - (di._normSub[j - di.numStart()] * di._normMul[j-di.numStart()]);
              double diff = Math.abs(goldValue - thisValue);
              if (diff > 1e-12)
                throw new RuntimeException("bonk");
            }
          }
        }
      }.doAll(di._adaptedFrame);
    } finally {
      di.dropInteractions();
      di.remove();
    }
  }
}
