package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.util.ArrayUtils;
import water.util.Log;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Created by tomas on 11/6/16.
 */
public class LocalMRTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(3); }
  private static class MrFunTest1 extends MrFun<MrFunTest1>{
    int _exId;
    public int [] _val;
    public MrFunTest1(int exId){_exId = exId;}
    public void map(int id){
      if(_val == null)_val = new int[]{id};
      else _val = ArrayUtils.append(_val,id);
    }
    public void reduce(MrFunTest1 other){
      if(_val == null) _val = other._val;
      else if(other._val != null) _val = ArrayUtils.sortedMerge(_val,other._val);
    }
  }

  private void testCnt(int cnt){
    MrFunTest1 f = new MrFunTest1(-1);
    H2O.submitTask(new LocalMR(f,cnt)).join();
    assertEquals(cnt,f._val.length);
    for(int i = 0; i < cnt; ++i)
      assertEquals(i,f._val[i]);
  }
  @Test
  public void testNormal() {
    testCnt(1);
    testCnt(2);
    testCnt(3);
    testCnt(4);
    testCnt(5);
    testCnt(10);
    testCnt(15);
    testCnt(53);
    testCnt(64);
    testCnt(100);
    testCnt(111);
  }

  @Test
  public void testIAE() {
    try {
      testCnt(0);
      assertTrue("should've thrown IAE",false);
    } catch(IllegalArgumentException e){}
    try {
      testCnt(-1);
      assertTrue("should've thrown IAE",false);
    } catch(IllegalArgumentException e){}
  }

  private static class TestException extends RuntimeException {}

  private static class MrFunTest2 extends MrFun<MrFunTest2>{
    final int exId;
    String s;
    AtomicInteger _active;

    public MrFunTest2(int exId, AtomicInteger activeCnt){this.exId = exId; _active = activeCnt;}
    @Override
    protected void map(int id) {
      if (id % exId == 0) throw new TestException();
      _active.incrementAndGet();
      try {Thread.sleep(10);} catch (InterruptedException e) {}
      s = "" + id;
      _active.decrementAndGet();
    }
    @Override public void reduce(MrFunTest2 other){
      s = s + ", " + other.s;
    }
  }
  @Test
  public void testThrow() {
    long seed = 87654321;
    Random rnd = new Random(seed);
    for(int k = 0; k < 10; ++k){
      int cnt = Math.max(1,rnd.nextInt(50));
      final int exId = Math.max(1,rnd.nextInt(cnt));
      final AtomicInteger active = new AtomicInteger();
      // test correct throw behavior with blocking call
      try {
        H2O.submitTask(new LocalMR(new MrFunTest2(exId,active),cnt)).join();
        assertTrue("should've thrown test exception",false);
      } catch(TestException t) {
        assertEquals(0,active.get());
      }
      // and with completer
      try {
        H2O.H2OCountedCompleter cc = new H2O.H2OCountedCompleter(){};
        H2O.submitTask(new LocalMR(new MrFunTest2(exId,active),cnt,cc));
        cc.join();
        assertTrue("should've thrown test exception",false);
      } catch(TestException t) {
        assertEquals(0,active.get());
      }
    }
  }

  @Test
  public void testShowLocalMRNotReproducibleByDefault() {
    Random rnd = new Random(0xCAFE);
    double[] data = new double[1000];
    for (int i = 0; i < data.length; i++) {
      data[i] = rnd.nextDouble();
    }
    double[] runs = new double[100];
    for (int i = 0; i < runs.length; i++) {
      MrFunSum funSum = new MrFunSum(data);
      H2O.submitTask(new LocalMR<MrFunSum>(funSum, data.length)).join();
      runs[i] = funSum._total;
    }
    Log.info("Runs: " + Arrays.toString(runs));
    assertNotEquals("All runs produce the same result (that could be good!), it means either:" +
                    "a) the problem was fixed, b) the test is flaky and sometimes only sometimes - try to improve it!", 
            ArrayUtils.minIndex(runs), ArrayUtils.maxIndex(runs));
  }

  @Test
  public void testWithNoPrevTaskReuseMakesLocalMRReproducible() {
    Random rnd = new Random(0xCAFE);
    double[] data = new double[1000];
    for (int i = 0; i < data.length; i++) {
      data[i] = rnd.nextDouble();
    }
    double[] runs = new double[100];
    double expected = 0;
    for (int i = 0; i < runs.length; i++) {
      MrFunSum funSum = new MrFunSum(data);
      LocalMR<MrFunSum> localMR = new LocalMR<MrFunSum>(funSum, data.length)
              .withNoPrevTaskReuse(); // make it reproducible
      H2O.submitTask(localMR).join();
      if (i == 0)
        expected = funSum._total;
      else
        assertEquals("All runs were supposed to produce the same result", expected, funSum._total, 0);
    }
  }

  private static class MrFunSum extends MrFun<MrFunSum>{
    public double _total;
    public transient double[] _data;

    public MrFunSum() {
    }
    
    private MrFunSum(double[] data) {
      _data = data;
    }

    @Override
    public void map(int id) {
      for (int i = 0; i < id; i++)
        _total += _data[id];
    }

    @Override
    public void reduce(MrFunSum other) {
      _total += other._total;
    }
  }

}
