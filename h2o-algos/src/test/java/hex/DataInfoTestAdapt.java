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
    Model.InteractionSpec interactions = Model.InteractionSpec.allPairwise(new String[]{"class", "sepal_len"});

    boolean useAll=false;
    boolean standardize=false;  // golden frame is standardized before splitting, while frame we want to check would be standardized post-split (not exactly what we want!)
    boolean skipMissing=true;
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
      fr.swap(3, 4);
      expanded = GLMModel.GLMOutput.expand(fr, interactions, useAll, standardize,skipMissing);   // here's the "golden" frame

      // now split fr and expanded
      long seed;
      frSplits = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed = new Random().nextLong());
      expandSplits = ShuffleSplitFrame.shuffleSplitFrame(expanded, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed);

      // check1: verify splits. expand frSplits with DataInfo and check against expandSplits
      checkSplits(frSplits,expandSplits,interactions,useAll,standardize);

      // now take the test frame from frSplits, and adapt it to a DataInfo built on the train frame
      dinfo = makeInfo(frSplits[0], interactions, useAll, standardize);
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._response_column = "petal_wid";
      Model.InteractionBuilder interactionBldr = interactionBuilder(dinfo);
      Model.adaptTestForTrain(frSplits[1],null,null,dinfo._adaptedFrame.names(),dinfo._adaptedFrame.domains(),parms,true,false, interactionBldr,null,null, null, false);
      scoreInfo = dinfo.scoringInfo(dinfo._adaptedFrame._names,frSplits[1]);
      checkFrame(scoreInfo,expandSplits[1]);
    } finally {
      cleanup(fr,expanded);
      cleanup(frSplits);
      cleanup(expandSplits);
      cleanup(dinfo, scoreInfo);
    }
  }


  @Test public void testInteractionTrainTestSplitAdaptAirlines() {
    DataInfo dinfo=null, scoreInfo=null;
    Frame frA=null, fr=null, expanded=null;
    Frame[] frSplits=null, expandSplits=null;
    Model.InteractionSpec interactions = Model.InteractionSpec.allPairwise(new String[]{"CRSDepTime", "Origin"});

    String[] keepColumns = new String[]{
            "Year",           "Month"     ,     "DayofMonth" ,    "DayOfWeek",
            "CRSDepTime" ,    "CRSArrTime"   , "UniqueCarrier" , "CRSElapsedTime",
            "Origin"     ,    "Dest"      ,     "Distance"  ,    "IsDepDelayed",

    };

    boolean useAll=false;
    boolean standardize=false;  // golden frame is standardized before splitting, while frame we want to check would be standardized post-split (not exactly what we want!)
    boolean skipMissing=false;
    try {
      frA = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
      fr = frA.subframe(keepColumns);
      expanded = GLMModel.GLMOutput.expand(fr, interactions, useAll, standardize, skipMissing);   // here's the "golden" frame

      // now split fr and expanded
      long seed;
      frSplits = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed = new Random().nextLong());
      expandSplits = ShuffleSplitFrame.shuffleSplitFrame(expanded, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, seed);

      // check1: verify splits. expand frSplits with DataInfo and check against expandSplits
      checkSplits(frSplits,expandSplits,interactions,useAll,standardize,skipMissing);

      // now take the test frame from frSplits, and adapt it to a DataInfo built on the train frame
      dinfo = makeInfo(frSplits[0], interactions, useAll, standardize,skipMissing);
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._response_column = "IsDepDelayed";
      Model.InteractionBuilder interactionBldr = interactionBuilder(dinfo);
      Model.adaptTestForTrain(frSplits[1],null,null,dinfo._adaptedFrame.names(),dinfo._adaptedFrame.domains(),parms,true,false,interactionBldr,null,null,null, false);

      scoreInfo = dinfo.scoringInfo(dinfo._adaptedFrame._names,frSplits[1]);
      checkFrame(scoreInfo,expandSplits[1], skipMissing);

    } finally {
      cleanup(fr,frA,expanded);
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


  private void checkSplits(Frame frSplits[], Frame goldSplits[], Model.InteractionSpec interactions, boolean useAll, boolean standardize) {
    checkSplits(frSplits,goldSplits,interactions,useAll,standardize,false);
  }

  private void checkSplits(Frame frSplits[], Frame goldSplits[], Model.InteractionSpec interactions, boolean useAll, boolean standardize, boolean skipMissing) {
    for(int i=0;i<frSplits.length;++i)
      checkFrame(makeInfo(frSplits[i],interactions,useAll,standardize,skipMissing),goldSplits[i], skipMissing);
  }

  private static DataInfo makeInfo(Frame fr, Model.InteractionSpec interactions, boolean useAll, boolean standardize) {
    return makeInfo(fr,interactions,useAll,standardize,true);
  }
  private static DataInfo makeInfo(Frame fr, Model.InteractionSpec interactions, boolean useAll, boolean standardize, boolean skipMissing) {
    return new DataInfo(
            fr,          // train
            null,        // valid
            1,           // num responses
            useAll,        // use all factor levels
            standardize?DataInfo.TransformType.STANDARDIZE:DataInfo.TransformType.NONE,  // predictor transform
            DataInfo.TransformType.NONE,  // response  transform
            skipMissing, // skip missing
            false,       // impute missing
            false,       // missing bucket
            false,       // weight
            false,       // offset
            false,       // fold
            interactions // interactions
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

  private void checkFrame(final DataInfo di, final Frame gold) { checkFrame(di,gold,true); }

  private void checkFrame(final DataInfo di, final Frame gold, final boolean skipMissing) {
    try {
      Vec[] vecs = new Vec[di._adaptedFrame.numCols()+gold.numCols()];
      System.arraycopy(di._adaptedFrame.vecs(),0,vecs,0,di._adaptedFrame.numCols());
      System.arraycopy(gold.vecs(), 0, vecs, di._adaptedFrame.numCols(), gold.numCols());
      new MRTask() {
        @Override public void map(Chunk[] cs) {
          int off = di._adaptedFrame.numCols();
          DataInfo.Row r = di.newDenseRow();
//          DataInfo.Row rows[] = di.extractSparseRows(cs);
          for (int i = 0; i < cs[0]._len; ++i) {
//            DataInfo.Row r = rows[i];
            di.extractDenseRow(cs, i, r);
            if( skipMissing && r.isBad() ) continue;
            for (int j = 0; j < di.fullN(); ++j) {
              double goldValue = cs[off+j].atd(i);
              double thisValue = r.get(j); // - (di._normSub[j - di.numStart()] * di._normMul[j-di.numStart()]);
              double diff = Math.abs(goldValue - thisValue);
              if (diff > 1e-12) {
                if( !skipMissing && diff < 10 )
                  System.out.println("row mismatch: " + i + " column= " + j  + "; diff= " + diff + " but not skipping missing, so due to discrepancies in taking mean on split frames");
                else throw new RuntimeException("bonk");
              }
            }
          }
        }
      }.doAll(vecs);
    } finally {
      di.dropInteractions();
      di.remove();
    }
  }

  private static Model.InteractionBuilder interactionBuilder(final DataInfo dataInfo) {
    return new Model.InteractionBuilder() {
      @Override
      public Frame makeInteractions(Frame f) {
        Model.InteractionPair[] interactionPairs = dataInfo._interactionSpec.makeInteractionPairs(f);
        f.add(Model.makeInteractions(f, false, interactionPairs, true, true, false));
        return f;
      }
    };
  }

}
