package hex.example;

import hex.ModelMetrics;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import java.io.File;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;
import org.joda.time.*;

@Ignore("Test DS end-to-end workflow; not intended as a junit yet")
public class WorkFlowTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  // Test DS end-to-end workflow on a small dataset
  @Test
  public void testWorkFlowSmall() {
    String[] files = { "bigdata/laptop/citibike-nyc/2013-10.csv" };
    testWorkFlow(files);
  }

  // Test DS end-to-end workflow on a small dataset
  @Test @Ignore
  public void testWorkFlowBig() {
    String[] files = {
      "bigdata/laptop/citibike-nyc/2013-07.csv",
      "bigdata/laptop/citibike-nyc/2013-08.csv",
      "bigdata/laptop/citibike-nyc/2013-09.csv",
      "bigdata/laptop/citibike-nyc/2013-10.csv",
      "bigdata/laptop/citibike-nyc/2013-11.csv",
      "bigdata/laptop/citibike-nyc/2013-12.csv",
      "bigdata/laptop/citibike-nyc/2014-01.csv",
      "bigdata/laptop/citibike-nyc/2014-02.csv",
      "bigdata/laptop/citibike-nyc/2014-03.csv",
      "bigdata/laptop/citibike-nyc/2014-04.csv",
      "bigdata/laptop/citibike-nyc/2014-05.csv",
      "bigdata/laptop/citibike-nyc/2014-06.csv",
      "bigdata/laptop/citibike-nyc/2014-07.csv",
      "bigdata/laptop/citibike-nyc/2014-08.csv" };
    testWorkFlow(files);
  }

  // End-to-end workflow test:
  // 1- load set of files, train, test, holdout
  // 2- light data munging
  // 3- build model on train; using test as validation
  // 4- score on holdout set
  //
  // If files are missing, silently fail - as the files are big and this is not
  // yet a junit test
  private void testWorkFlow(String[] files) {
    try {
      Scope.enter();

      // 1- Load datasets
      Frame data = load_files("data.hex",files);
      if( data == null ) return;
  
  
      // -------------------------------------------------
      // 2- light data munging
  
      // Convert start times to: Month, Weekday, Hour
      Vec startime = data.vec("starttime");
      data.add(new TimeSplit().doIt(startime,"s"));

      // Now do a monster Group-By.  Count bike starts per-station per-hour per-month.
      long start = System.currentTimeMillis();
      Frame bph = new CountBikes().doAll(data.vec("s_Month"),data.vec("s_Day"),data.vec("s_Hour"),data.vec("start station name")).makeFrame(Key.make("bph.hex"));
      System.out.println("Groupby took "+(System.currentTimeMillis()-start));
      data.remove();

      // Split into train, test and holdout sets
      Key[] keys = new Key[]{Key.make("train.hex"),Key.make("test.hex"),Key.make("hold.hex")};
      double[] ratios = new double[]{0.6,0.3,0.1};
      Frame[] frs = ShuffleSplitFrame.shuffleSplitFrame(bph,keys,ratios,1234567689L);
      Frame train = frs[0];
      Frame test  = frs[1];
      Frame hold  = frs[2];
      bph.remove();
      System.out.println(train);
  

      // -------------------------------------------------
      // 3- build model on train; using test as validation

      // ---
      // Gradient Boosting Method
      GBMModel.GBMParameters gbm_parms = new GBMModel.GBMParameters();
      // base Model.Parameters
      gbm_parms._train = train._key;
      gbm_parms._valid = test ._key;
      gbm_parms._score_each_iteration = false; // default is false
      // SupervisedModel.Parameters
      gbm_parms._response_column = "bikes";
      gbm_parms._convert_to_enum = false; // regression
      
      // SharedTreeModel.Parameters
      gbm_parms._ntrees = 100;        // default is 50, 1000 is 0.90, 10000 is 0.91
      gbm_parms._max_depth = 7;       // default is 5
      gbm_parms._min_rows = 10;       // default
      gbm_parms._nbins = 20;          // default
      
      // GBMModel.Parameters
      gbm_parms._loss = GBMModel.GBMParameters.Family.AUTO; // default
      gbm_parms._learn_rate = 0.3f;   // default

      // Train model; block for results
      Job<GBMModel> job = new GBM(gbm_parms).trainModel();
      GBMModel gbm = job.get();
      job.remove();
      
      // ---
      // Build a GLM model also
      GLMModel.GLMParameters glm_parms = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gaussian);
      // base Model.Parameters
      glm_parms._train = train._key;
      glm_parms._valid = test ._key;
      glm_parms._score_each_iteration = false; // default is false
      // SupervisedModel.Parameters
      glm_parms._response_column = "bikes";
      glm_parms._convert_to_enum = false; // regression
  
      // GLMModel.Parameters
      glm_parms._use_all_factor_levels = true;
  
      // Train model; block for results
      Job<GLMModel> glm_job = new GLM(glm_parms).trainModel();
      GLMModel glm = glm_job.get();
      glm_job.remove();
  
      // -------------------------------------------------
      // 4- Score on holdout set & report
      gbm.score(train).remove();
      double train_r2_gbm = ModelMetrics.getFromDKV(gbm, train).r2();
      gbm.score(test ).remove();
      double  test_r2_gbm = ModelMetrics.getFromDKV(gbm, test ).r2();
      gbm.score(hold ).remove();
      double  hold_r2_gbm = ModelMetrics.getFromDKV(gbm, hold ).r2();
      System.out.println("GBM R2 TRAIN="+train_r2_gbm+", R2 TEST="+test_r2_gbm+", R2 HOLDOUT="+hold_r2_gbm);
      gbm.remove();

      glm.score(train).remove();
      double train_r2_glm = ModelMetrics.getFromDKV(glm, train).r2();
      glm.score(test ).remove();
      double  test_r2_glm = ModelMetrics.getFromDKV(glm, test ).r2();
      glm.score(hold ).remove();
      double  hold_r2_glm = ModelMetrics.getFromDKV(glm, hold ).r2();
      System.out.println("GLM R2 TRAIN="+train_r2_glm+", R2 TEST="+test_r2_glm+", R2 HOLDOUT="+hold_r2_glm);
      glm.remove();

      // Cleanup
      train.remove();
      test .remove();
      hold .remove();

    } finally {
      Scope.exit();
    }
  }

  // Load a set of files, then parse them all together
  private Frame load_files(String hex, String[] fnames) {
    long start = System.currentTimeMillis();
    System.out.print("Loading "+hex+"...");
    try {
      Key keys[] = new Key[fnames.length];
      for( int i=0; i<fnames.length; i++ ) {
        File f = new File(fnames[i]);
        if( !f.exists() ) {
          System.out.println("File "+fnames[i]+" missing, aborting test");
          return null;
        }
        keys[i] = NFSFileVec.make(f)._key;
      }
      return ParseDataset.parse(Key.make(hex),keys);
    } finally {
      System.out.println("loaded in "+(System.currentTimeMillis()-start)/1000.0+"secs");
    }
  }

  // Split out Month, DayOfWeek and HourOfDay from Unix Epoch msec
  class TimeSplit extends MRTask<TimeSplit> {
    public Frame doIt(Vec time, String title) {
      String[] colNames = new String[]{title+"_Month", title+"_Day", title+"_Hour"};
      String[][] domains = new String[][] {
        {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"},
        {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}, // Order comes from JODA
        null
      };
      return doAll(3, time).outputFrame(colNames, domains);
    }

    @Override public void map(Chunk chk[], NewChunk[] ncs) {
      Chunk msec = chk[0];
      NewChunk month = ncs[0];
      NewChunk wkday = ncs[1];
      NewChunk hour  = ncs[2];
      MutableDateTime mdt = new MutableDateTime(); // Recycle same MDT
      for( int i=0; i<msec._len; i++ ) {
        mdt.setMillis(msec.at8(i)); // Set time in msec of unix epoch
        month.addNum(mdt.getMonthOfYear()-1); // Convert to 0-based from 1-based
        wkday.addNum(mdt.getDayOfWeek()  -1); // Convert to 0-based from 1-based
        hour .addNum(mdt.getHourOfDay()    ); // zero based already
      }
    }
  }

  // Monster Group-By.  Count bike starts per-station per-hour per-month.
  class CountBikes extends MRTask<CountBikes> {
    int _bikes[/*month*//*day*//*hour*//*station*/];
    int _num_sid;
    private int idx( long mon, long day, long hr, long sid ) {
      return (int)(((mon*7+day)*24+hr)*_num_sid+sid);
    }
    @Override public void map( Chunk chk[] ) {
      Chunk mon = chk[0];
      Chunk day = chk[1];
      Chunk hr  = chk[2];
      Chunk sid = chk[3];
      _num_sid = sid.vec().cardinality();
      int len = chk[0]._len;
      _bikes = new int[idx(12-1,7-1,24-1,_num_sid-1)+1];
      for( int i=0; i<len; i++ )
        _bikes[idx(mon.at8(i),day.at8(i),hr.at8(i),sid.at8(i))]++;
    }
    @Override public void reduce( CountBikes cb ) {
      water.util.ArrayUtils.add(_bikes,cb._bikes);
    }

    Frame makeFrame(Key key) {
      final int ncols = 5;
      AppendableVec[] avecs = new AppendableVec[ncols];
      NewChunk ncs[] = new NewChunk[ncols];
      Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(ncols);
      for( int c = 0; c < avecs.length; c++ )
        avecs[c] = new AppendableVec(keys[c]);

      Futures fs = new Futures();
      int chunknum=0;

      for( int mon = 0; mon < 12; mon++ ) {
        for( int day = 0; day < 7; day++ ) {
          for( int hr = 0; hr < 24; hr++ ) {
            for( int sid = 0; sid < _num_sid; sid++ ) {
              int bikecnt = _bikes[idx(mon,day,hr,sid)];
              if( bikecnt == 0 ) continue;
              if( ncs[0] == null )  
                for( int c=0; c<ncols; c++ ) 
                  ncs[c] = new NewChunk(avecs[c],chunknum);
              ncs[0].addNum(mon);
              ncs[1].addNum(day);
              ncs[2].addNum(hr);
              ncs[3].addNum(sid);
              ncs[4].addNum(bikecnt);
            }
          }
          if( ncs[0] != null ) {
            for( int c=0; c<ncols; c++ ) ncs[c].close(chunknum,fs);
            chunknum++;
            ncs[0] = null;
          }
        }
      }

      Vec[] vecs = new Vec[ncols];
      for( int c = 0; c < avecs.length; c++ ) {
        vecs[c] = avecs[c].close(fs);
        if( c < _fr.numCols() )
          vecs[c].setDomain(_fr.vecs()[c].domain());
      }
      fs.blockForPending();
      Frame fr = new Frame(key,new String[]{"Month","Day","Hour","Station","bikes"}, vecs);
      DKV.put(fr);
      return fr;
    }
  }
}
