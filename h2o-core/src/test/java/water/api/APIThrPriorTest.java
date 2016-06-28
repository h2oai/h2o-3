package water.api;

import hex.*;
import hex.schemas.ModelBuilderSchema;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.api.schemas3.ModelParametersSchemaV3;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;
import java.util.Properties;


/** Test that short, interactive work runs at a higher priority than long
 *  running model-building work. */
public class APIThrPriorTest extends TestUtil {
  @BeforeClass static public void setup() { 
    stall_till_cloudsize(5);
    H2O.finalizeRegistration();
  }

  @Test public void testAPIThrPriorities() throws IOException {
    Frame fr = null;
    Bogus blder = null;
    Job<BogusModel> job = null;
    Vec vec = null;
    try {
      // Get some keys & frames loaded
      fr = parse_test_file(Key.make("iris.hex"),"smalldata/iris/iris_wheader.csv");
      vec = Vec.makeZero(100);

      // Basic test plan:
  
      // Start a "long running model-builder job".  This job will start using the
      // nomial model-builder strategy, then block in the driver "as if" it's
      // working hard.  Imagine DL slamming all cores.  We record the F/J
      // priority we're running on.
      //
      // Then we make a REST-style call thru RequestServer looking for some
      // stuff; list all frames, cloud status, view a frame (rollups).  During
      // these actions we record F/J queue priorities - and assert this work is
      // all running higher than the DL/model-build priority.

      // TODO: Make a more sophisticated builder that launches a MRTask internally,
      // which blocks on ALL NODES - before we begin doing rollups.  Then check
      // the rollups priorities ON ALL NODES, not just this one.
    
      // Build and launch the builder
      BogusModel.BogusParameters parms = new BogusModel.BogusParameters();
      blder = new Bogus(parms);
      job = blder.trainModel();
  
      // Block till the builder sets _driver_priority, and is blocked on state==1
      synchronized(blder) {
        while( blder._state == 0 ) try { blder.wait(); } catch (InterruptedException ignore) { }
        assert blder._state == 1;
      }
      int driver_prior = blder._driver_priority;
      Properties urlparms;
  
      // Now that the builder is blocked at some priority, do some GUI work which
      // needs to be at a higher priority.  It comes in on a non-FJ thread
      // (probably Nano or Jetty) but anything that hits the F/J queue needs to
      // be higher
      Assert.assertEquals(0,H2O.LOW_PRIORITY_API_WORK);
      Assert.assertNull(H2O.LOW_PRIORITY_API_WORK_CLASS);
      H2O.LOW_PRIORITY_API_WORK = driver_prior+1;

      // Many URLs behave.
      // Broken hack URLs:
      serve("/",null,301);
      serve("/junk",null,404);
      serve("/HTTP404", null,404);
      // Basic: is H2O up?
      serve("/3/Cloud",null,200);
      serve("/3/About", null,200);

      // What is H2O doing?
      urlparms = new Properties();
      urlparms.setProperty("depth","10");
      serve("/3/Profiler", urlparms,200);
      serve("/3/JStack", null,200);
      serve("/3/KillMinus3", null,200);
      serve("/3/Timeline", null,200);
      serve("/3/Jobs", null,200);
      serve("/3/WaterMeterCpuTicks/0", null,200);
      serve("/3/WaterMeterIo", null,200);
      serve("/3/Logs/download", null,200);
      serve("/3/NetworkTest", null,200);

      // Rollup stats behave
      final Key rskey = vec.rollupStatsKey();
      Assert.assertNull(DKV.get(rskey)); // Rollups on my zeros not computed yet
      vec.sigma();
      Assert.assertNotNull(DKV.get(rskey)); // Rollups on my zeros not computed yet
      serve("/3/Frames/iris.hex", null,200); // Rollups already done at parse, but gets ChunkSummary

      // Convenience; inspection of simple stuff
      urlparms = new Properties();
      urlparms.setProperty("src","./smalldata/iris");
      serve("/3/Typeahead/files", urlparms,200);
      urlparms = new Properties();
      urlparms.setProperty("key","iris.hex");
      urlparms.setProperty("row","0");
      urlparms.setProperty("match","foo");
      serve("/3/Find", urlparms,200);
      serve("/3/Metadata/endpoints", null,200);
      serve("/3/Frames", null,200);
      serve("/3/Models", null,200);
      serve("/3/ModelMetrics", null,200);
      serve("/3/NodePersistentStorage/configured", null,200);

      // Recovery
      //serve("/3/Shutdown", null,200); // OOPS!  Don't really want to run this one, unless we're all done with testing
      serve("/3/DKV", null,200,"DELETE"); // delete must happen after rollups above!
      serve("/3/LogAndEcho", null,200,"POST");
      serve("/3/InitID", null,200);
      serve("/3/GarbageCollect", null,200,"POST");

      // Turn off debug tracking
      H2O.LOW_PRIORITY_API_WORK = 0;
      H2O.LOW_PRIORITY_API_WORK_CLASS = null;
      // Allow the builder to complete.
      DKV.put(job); // reinstate the JOB in the DKV, because JOB demands it.
      synchronized(blder) { blder._state = 2; blder.notify(); }
      job.get();                  // Block for builder to complete
    } finally {
      // Turn off debug tracking
      H2O.LOW_PRIORITY_API_WORK = 0;
      H2O.LOW_PRIORITY_API_WORK_CLASS = null;
      if( blder != null )
        synchronized(blder) { blder._state = 2; blder.notify(); }
      if( job != null ) job.remove();
      if( vec != null ) vec.remove();
      if( fr != null ) fr.delete();
    }
  }

  private void serve(String s, Properties parms, int status) throws IOException {
    serve(s,parms,status,"GET");
  }
  private void serve(String s, Properties parms, int status, String method) throws IOException {
    NanoResponse r = RequestServer.serve(s,method,null,parms==null?new Properties():parms);
    int n = r.data.available();
    byte[] bs = new byte[n];
    r.data.read(bs,0,n);
    String ss = new String(bs); // Computed to help with debugging
    Assert.assertEquals(status,Integer.parseInt(r.status.split(" ")[0]));
    Assert.assertNull("" + s, H2O.LOW_PRIORITY_API_WORK_CLASS);
  }
}

// Empty model
class BogusModel extends Model<BogusModel,BogusModel.BogusParameters,BogusModel.BogusOutput> {
  public static class BogusParameters extends Model.Parameters {
    public String algoName() { return "Bogus"; }
    public String fullName() { return "Bogus"; }
    public String javaName() { return BogusModel.class.getName(); }
    @Override public long progressUnits() { return 0; }
  }
  public static class BogusOutput extends Model.Output { }
  BogusModel( Key selfKey, BogusParameters parms, BogusOutput output) { super(selfKey,parms,output); }
  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) { throw H2O.fail(); }
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) { throw H2O.fail(); }
}

// Do nothing builder; does not even make a Bogus model.
// But blocks in the driver's compute2() "as if" it's slamming all cores.
class Bogus extends ModelBuilder<BogusModel,BogusModel.BogusParameters,BogusModel.BogusOutput> {
  // 0: driver goes, test waits
  // 1: priority set, driver waits, test goes
  // 2: driver goes, test waits
  volatile int _state;
  int _driver_priority = -1;
  @Override public ModelCategory[] can_build() { return null; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  public Bogus( BogusModel.BogusParameters parms ) { super(parms); init(false); }
  @Override protected Driver trainModelImpl() { return new BogusDriver(); }
  @Override public void init(boolean expensive) { super.init(expensive); }

  private class BogusDriver extends Driver {
    @Override public void computeImpl() {
      _driver_priority = priority(); // Get H2OCountedCompleter priority
      synchronized(Bogus.this) {
        if( _state == 0 ) _state = 1;
        Bogus.this.notify();
        while( _state==1 ) try { Bogus.this.wait(); } catch (InterruptedException ignore) { }
      }
    }
  }
}

// Need this class, so a /3/Jobs can return the JSON'd version of it
class BogusV3 extends ModelBuilderSchema<Bogus,BogusV3,ModelParametersSchemaV3> {}

