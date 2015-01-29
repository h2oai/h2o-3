package hex.example;

import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
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
      "bigdata/laptop/citibike-nyc/2013-01.csv",
      "bigdata/laptop/citibike-nyc/2013-02.csv",
      "bigdata/laptop/citibike-nyc/2013-03.csv",
      "bigdata/laptop/citibike-nyc/2013-04.csv",
      "bigdata/laptop/citibike-nyc/2013-05.csv",
      "bigdata/laptop/citibike-nyc/2013-06.csv",
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

    // 1- Load datasets
    Frame data = load_files("data.hex",files);
    if( data == null ) return;


    // 2- light data munging

    // Drop station id, as it's redundant with the name
    data.remove("start station id").remove();
    data.remove(  "end station id").remove();

    // Convert stop & start times to: Month, Weekday, Hour
    Vec startime = data.remove("starttime");
    data.add(new TimeSplit().doIt(startime,"s"));
    startime.remove();

    Vec stoptime = data.remove("stoptime");
    data.add(new TimeSplit().doIt(stoptime,"e"));
    stoptime.remove();

    // Split into train, test and holdout sets
    Key[] keys = new Key[]{Key.make("train.hex"),Key.make("test.hex"),Key.make("hold.hex")};
    double[] ratios = new double[]{0.6,0.3,0.1};
    Frame[] frs = ShuffleSplitFrame.shuffleSplitFrame(data,keys,ratios,1234567689L);
    Frame train = frs[0];
    Frame test  = frs[1];
    Frame hold  = frs[2];
    data.remove();

    System.out.println(train);
    System.out.println(test);

    // 3- build model on train; using test as validation
    GBMModel.GBMParameters gbm_parms = new GBMModel.GBMParameters();
    // base Model.Parameters
    gbm_parms._train = train._key;
    gbm_parms._valid = test ._key;
    gbm_parms._score_each_iteration = false; // default is false
    // SupervisedModel.Parameters
    gbm_parms._response_column = "tripduration";
    gbm_parms._convert_to_enum = false; // regression

    // SharedTreeModel.Parameters
    gbm_parms._ntrees = 200;        // default is 50
    gbm_parms._max_depth = 7;       // default
    gbm_parms._min_rows = 10;       // default
    gbm_parms._nbins = 20;          // default

    // GBMModel.Parameters
    gbm_parms._loss = GBMModel.GBMParameters.Family.AUTO; // default
    gbm_parms._learn_rate = 0.1f;   // default

    Job<GBMModel> job = new GBM(gbm_parms).trainModel();
    GBMModel gbm = job.get();
    job.remove();

    System.out.println(Arrays.toString(gbm._output._mse_train));
    System.out.println(Arrays.toString(gbm._output._mse_valid));

    // Score on holdout set & report
    



    // Cleanup
    gbm.remove();

    train.remove();
    test .remove();
    hold .remove();
  }

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


  private Frame load_files(String hex, String[] fnames) {
    long start = System.currentTimeMillis();
    System.out.print("Loading "+hex+"...");
    try {
      Key keys[] = new Key[fnames.length];
      for( int i=0; i<fnames.length; i++ ) {
        File f = new File(fnames[i]);
        if( !f.exists() ) return null;
        keys[i] = NFSFileVec.make(f)._key;
      }
      return ParseDataset.parse(Key.make(hex),keys);
    } finally {
      System.out.println("loaded in "+(System.currentTimeMillis()-start)/1000.0+"secs");
    }
  }

}
