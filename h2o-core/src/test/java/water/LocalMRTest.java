package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.util.ArrayUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomas on 11/6/16.
 */
public class LocalMRTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }
  private static class MrFunTest1 extends MrFun<MrFunTest1>{
    int _exId;
    public int [] _val;
    public MrFunTest1(int exId){_exId = exId;}
    public void map(int id){
      _val = new int[]{id};
    }
    public void reduce(MrFunTest1 other){
      _val = ArrayUtils.sortedMerge(_val,other._val);
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
}
