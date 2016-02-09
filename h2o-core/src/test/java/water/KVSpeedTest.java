package water;

import org.junit.*;
import java.util.ArrayList;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.util.PrettyPrint;

@Ignore("Speed/perf test, not intended as a pre-push junit test")
public class KVSpeedTest extends TestUtil {

  static final int NCLOUD=5;
  static final int NKEYS=1000000;
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  // Make a million keys-per-node.  Make sure they are all cached/shared on at
  // least one other node.  Time removing them all.  Can be network bound as
  // the million read/write/put/invalidates hit the wires.
  @Test @Ignore
  public void testMillionRemoveKeys() {
    long start = System.currentTimeMillis();

    // Compute home keys
    byte[] homes = new byte[NKEYS*NCLOUD];
    for( int i=0; i<homes.length; i++ )
      homes[i] = (byte)Key.make("Q"+i).home(H2O.CLOUD);
    final Key k = Key.make("homes");
    DKV.put(k,new Value(k,homes));
    start = logTime(start,"HOMEALL",NCLOUD);

    // Populate NKEYS locally
    new MRTask() {
      @Override protected void setupLocal() {
        byte[] homes = DKV.get(k).rawMem();
        final int sidx = H2O.SELF.index();
        long start = System.currentTimeMillis();
        for( int i=0; i<homes.length; i++ ) {
          if( homes[i]==sidx ) {
            String s = "Q"+i;
            Key k = Key.make(s);
            DKV.put(k,new Value(k,s),_fs);
          }
        }
        logTime(start, "PUT1 "+H2O.SELF, 1);
      }
    }.doAllNodes();
    start = logTime(start,"PUTALL",NCLOUD);

    // Force sharing at least once
    new MRTask() {
      @Override protected void setupLocal() {
        byte[] homes = DKV.get(k).rawMem();
        final int sidx = H2O.SELF.index();
        long start = System.currentTimeMillis();
        for( int i=0; i<homes.length; i++ )
          if( homes[i]==sidx )
            DKV.prefetch(Key.make("Q"+i+1));
        start = logTime(start, "PREFETCH1 "+H2O.SELF, 1);
        for( int i=0; i<homes.length; i++ )
          if( homes[i]==sidx )
            DKV.get(Key.make("Q"+i+1));
        logTime(start, "GET1 "+H2O.SELF, 1);
      }
    }.doAllNodes();
    start = logTime(start,"GETALL",NCLOUD);

    Futures fs = new Futures();
    for( int i=0; i<homes.length; i++ )
      DKV.remove(Key.make("Q"+i), fs);
    start = logTime(start,"REMALL_START",NCLOUD);
    fs.blockForPending();
    logTime(start,"REMALL_DONE",NCLOUD);

    DKV.remove(k);
    System.out.printf("STORE size = "+H2O.STORE.size());
    System.out.printf("STORE raw array length = "+H2O.STORE.raw_array().length);
  }

  @Test @Ignore
  public void testMillionInsertKeys() {
    final int PAR=100;
    final int NKEY=100000;     // PAR*NKEY = 100M keys
    final int WARMKEY=10000;    // PAR*WARMKEY = 1M keys
    H2O.H2OCountedCompleter foo = H2O.submitTask(new H2O.H2OCountedCompleter() {
        final Key  [][] keys = new Key  [PAR][NKEY];
        final Value[][] vals = new Value[PAR][NKEY];
        @Override public void compute2() {
          long now, start = System.currentTimeMillis();
          ArrayList<RecursiveAction> rs = new ArrayList<>();

          // Make a zillion keys
          for( int i = 0; i < PAR; ++i ) {
            final int fi = i;
            rs.add(new RecursiveAction() {
                @Override public void compute() {  // Make & insert Keys in parallel
                  for( int j = 0; j < NKEY; j++ )
                    keys[fi][j] = Key.make("Q"+(fi*NKEY+j));
                }
              });
          }
          ForkJoinTask.invokeAll(rs);
          now = System.currentTimeMillis();
          System.out.println("create msec="+(now-start)+", msec/op="+((double)(now-start))/PAR/NKEY);
          start = now;

          // Warmup hashmap
          for( int X=0; X<4; X++ ) {
            for( int i = 0; i < PAR; ++i ) {
              final int fi = i;
              rs.add(new RecursiveAction() {
                  @Override public void compute() {  // Make & insert Keys in parallel
                    for( int j = 0; j < WARMKEY; j++ ) {
                      Key k = keys[fi][j];
                      H2O.putIfMatch(k, vals[fi][j] = new Value(k, ""), null);
                    }
                  }
                });
            }
            ForkJoinTask.invokeAll(rs);

            for( int i = 0; i < PAR; ++i ) {
              final int fi = i;
              rs.set(fi,new RecursiveAction() {
                  @Override public void compute() {  // Make & insert Keys in parallel
                    for( int j = 0; j < WARMKEY; j++ )
                      H2O.putIfMatch(keys[fi][j], null, vals[fi][j]);
                  }
                });
            }
            ForkJoinTask.invokeAll(rs);

            now = System.currentTimeMillis();
            System.out.println("warmup msec="+(now-start)+", msec/op="+((double)(now-start))/PAR/WARMKEY);
            try { Thread.sleep(1000); } catch( InterruptedException ie ) {}
            start = System.currentTimeMillis();
          }
          System.out.println("Starting insert work");

          // Make a zillion Values in parallel
          for( int i = 0; i < PAR; ++i ) {
            final int fi = i;
            rs.add(new RecursiveAction() {
                @Override public void compute() {  // Make & insert Keys in parallel
                  for( int j = 0; j < NKEY; j++ )
                    vals[fi][j] = new Value(keys[fi][j], "");
                }
              });
          }
          ForkJoinTask.invokeAll(rs);
          now = System.currentTimeMillis();
          System.out.println("Values msec="+(now-start)+", msec/op="+((double)(now-start))/PAR/NKEY);
          start = now;

          // Insert a zillion keys in parallel
          for( int i = 0; i < PAR; ++i ) {
            final int fi = i;
            rs.add(new RecursiveAction() {
                @Override public void compute() {  // Make & insert Keys in parallel
                  for( int j = 0; j < NKEY; j++ ) {
                    Key k = keys[fi][j];
                    H2O.putIfMatch(k, vals[fi][j], null);
                  }
                }
              });
          }
          ForkJoinTask.invokeAll(rs);
          now = System.currentTimeMillis();
          System.out.println("insert msec="+(now-start)+", msec/op="+((double)(now-start))/PAR/NKEY);
          start = now;

          // Now remove them all
          for( int i = 0; i < PAR; ++i ) {
            final int fi = i;
            rs.set(fi,new RecursiveAction() {
                @Override public void compute() {  // Make & insert Keys in parallel
                  for( int j = 0; j < NKEY; j++ )
                    H2O.putIfMatch(keys[fi][j], null, vals[fi][j]);
                }
              });
          }
          ForkJoinTask.invokeAll(rs);
          now = System.currentTimeMillis();
          System.out.println("remove msec="+(now-start)+", msec/op="+((double)(now-start))/PAR/NKEY);
          start = now;

          tryComplete();
        }
      });
    foo.join();
    System.out.printf("STORE size = %d\n",H2O.STORE.size());
    System.out.printf("STORE raw array length = %d\n",H2O.STORE.raw_array().length);
  }

  private long logTime( long start, String msg, int ncloud ) {
    long now = System.currentTimeMillis();
    double msec_op = (double)(now-start)/NKEYS/ncloud;    
    System.out.println(msg+" "+ PrettyPrint.usecs((long)(msec_op*1000.0))+"/op");
    return now;
  }
}
