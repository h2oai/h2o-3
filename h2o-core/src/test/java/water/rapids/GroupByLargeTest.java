package water.rapids;

import java.io.File;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.util.Log;
import water.fvec.*;
import water.parser.ParseSetup;
import water.parser.ParseDataset;

public class GroupByLargeTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testSmallText() {
    System.out.println("Running small GroupBy ...");
    NFSFileVec nfs = NFSFileVec.make(find_test_file("smalldata/glm_test/citibike_small_train.csv"));
    Frame fr = new Frame(Key.make("raw_text"), new String[]{"text"}, new Vec[]{nfs});
    DKV.put(fr);
    String rapids = "(GB raw_text [0] nrow 0 \"all\")";

    //ASTGroup._testing_force_sorted = true;
    Val val = Exec.exec(rapids);
    ASTGroup._testing_force_sorted = false;
    fr.delete();
    System.out.println(val.toString());
    Frame res = val.getFrame();
    Assert.assertEquals( 2,res.numCols());
    Assert.assertEquals(67,res.numRows());

    chkFr(res, 0,10, 6240); // Group ASCII '\n'
    chkFr(res, 1,32,27693); // Group ASCII ' '
    chkFr(res, 2,34,39056); // Group ASCII '"'
    chkFr(res, 9,48,92388); // Group ASCII '0'
    chkFr(res,19,65, 3937); // Group ASCII 'A'

    res.delete();
  }

  // This test is TOO BIG to run as a junit, exists for debugging only.
  // Requires a 40G JVM.
  @Test public void testSixSense() {
    Log.info("Avito: Russian Craigslist Ad Click");
    String datapath = "../SixSense/";
    Frame tss=null, sif=null, gb=null;
    try {
      Log.info("Load search training data");
      tss = loadGiantFile(datapath+"trainSearchStream_20M.csv", "tss.hex", new String[]{"numeric","numeric","enum","enum","numeric","enum"});
      System.out.println(tss);
      
      Log.info("Load search data");
      sif = loadGiantFile(datapath+"Searchinfo.tsv", "sinfo.hex", new String[]{"numeric","time","numeric","numeric","enum","numeric","numeric","numeric","string"});
      System.out.println(sif);


      Log.info("Cleanup search data: replace marker values with None/missing, and convert to factors");
      Exec.exec("(= sinfo.hex NA \"CategoryID\" (== (cols sinfo.hex \"CategoryID\") 500001))",false);
      sif.replace(sif.find("CategoryID"),sif.vec("CategoryID").toCategoricalVec()).remove();
      Exec.exec("(= sinfo.hex NA \"LocationID\" (== (cols sinfo.hex \"LocationID\") 250001))",false);
      sif.replace(sif.find("LocationID"),sif.vec("LocationID").toCategoricalVec()).remove();
      System.out.println(sif);

      Log.info("GroupBy on SearchId; count searchs per user; popular searchs are more predictive");
      gb = Exec.exec("(GB sinfo.hex \"SearchID\" nrow \"SearchID\" \"rm\")").getFrame();
      System.out.println(gb);

//# Rename column; merge with original data
//#gb["num_search"] = gb.pop("nrow_SearchID").asfactor()
//gb["num_search"] = gb.pop("nrow_SearchID")
//trainSearchStream = trainSearchStream.merge(other=gb)

    } finally {
      if( tss != null ) tss.delete();
      if( sif != null ) sif.delete();
      if( gb  != null ) sif.delete();
    }
  }

  // Load a giant file, with named column types.  Silently return NULL if file
  // does not exist, since this test is TOO BIG to run as a junit, exists for
  // debugging only.
  private Frame loadGiantFile(String fname, String keyname, String[] coltypes) {
    File f = new File(fname);
    if( f == null || !f.exists() ) return null; // Silently abort if file not found.  
    NFSFileVec nfs = NFSFileVec.make(f);
    Key[] fkeys = new Key[]{nfs._key};
    ParseSetup ps = ParseSetup.guessSetup(fkeys,false,1);
    ps.setColumnTypes(coltypes);
    return ParseDataset.parse(Key.make(keyname), fkeys, true, ps);
  }


  private void chkFr( Frame fr, int row, long exp0, long exp1) {
    Assert.assertEquals(exp0, fr.vec(0).at8(row)); 
    Assert.assertEquals(exp1, fr.vec(1).at8(row)); 
  }
}
