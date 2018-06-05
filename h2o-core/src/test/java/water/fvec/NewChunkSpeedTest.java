package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.util.Log;

import static org.junit.Assert.assertTrue;

/***
 * This test is written to measure the speed with which NewChunks are written for various data types:
 * integer, long and double
 */

public class NewChunkSpeedTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  int rowNumber = 1000000;
  int rowInterval = 1000;
  double tolerance = 1e-10;
  int numberLoops=2;

  @Test public void testParseDoublesConst(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(true, false, false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for constant doubles:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseBigDoublesConst(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(true, true, false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for big constant doubles:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseFloatsConst(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(true, false, true);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for constant floats:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseFloats(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(false, false, true);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for floats:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseDoubles(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(false, false, false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for doubles:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseBigDoubles(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForDoubles(false, true, false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for big doubles:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseInteger(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForIntegers(false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for integers:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }

  @Test public void testParseIntegerConst(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForIntegers(true);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for constant integer:", " time(s) taken for "+numberLoops+" loops is "+endTime);
  }


  // todo: This should be changed to test after Spencer PR is in.
  @Test
  public void testParseLong(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForLongs(false);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("************************************************");
    Log.info("New Chunk test for longs:", " time(s) taken for "+numberLoops+" is "+endTime);
  }

  @Test public void testParseLongConsts(){
    double startTime = System.currentTimeMillis();
    for (int index=0; index<numberLoops; index++)
      testsForLongs(true);
    double endTime = (System.currentTimeMillis()-startTime)*0.001;  // change time to seconds
    Log.info("New Chunk test for constant longs:", " time(s) taken for "+numberLoops+" is "+endTime);
  }

  @Test
  public void testParseDataFromFiles() {
    String[] filenames = new String[]{"smalldata/jira/floatVals.csv", "smalldata/jira/integerVals.csv",
            "smalldata/jira/longVals.csv", "smalldata/jira/doubleVals.csv", "smalldata/jira/bigDoubleVals.csv"};
    int numLoops = 5 * numberLoops;

    for (int index = 0; index < filenames.length; index++) {
      double startTime = System.currentTimeMillis();
      for (int loop = 0; loop < numLoops; loop++) {
        Scope.enter();
        try {
          Frame f = parse_test_file(filenames[index]);
          assertTrue(f.numRows() == 100000);
          Scope.track(f);
        } finally {
          Scope.exit();
        }
      }
      double endTime = (System.currentTimeMillis() - startTime) * 0.001;  // change time to seconds
      Log.info("*******************************************************");
      Log.info("Parsing: " + filenames[index] + " time(s) taken for " + numLoops + " loops is " + endTime);
    }
  }

  public void testsForLongs(boolean forConstants) {
    Scope.enter();
    final long baseD = Long.MAX_VALUE-10*(long) rowNumber;

    try {
      Vec tVec = Vec.makeZero(rowNumber);
      Vec v;
      if (forConstants)
        v = new MRTask() {
          @Override public void map(Chunk cs) {
            for (int r=0; r<cs._len; r++){
              cs.set(r, baseD);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];
      else
        v = new MRTask() {
          @Override public void map(Chunk cs) {
            long rowStart = cs.start();
            for (int r=0; r<cs._len; r++){
              cs.set(r, r+baseD+rowStart);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];

      Scope.track(tVec);
      Scope.track(v);

      for (int rowInd=0; rowInd<rowNumber; rowInd=rowInd+rowInterval) {
        if (forConstants)
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (rowInd + baseD) + " v.at(rowIndex): "
                          + v.at8(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  v.at8(rowInd) == baseD);
        else
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (rowInd + baseD) + " v.at(rowIndex): "
                          + v.at8(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  v.at8(rowInd) == (rowInd + baseD));
      }
    } finally {
      Scope.exit();
    }
  }

  public void testsForIntegers(boolean forConstants){
    Scope.enter();
    final int baseD = Integer.MAX_VALUE-2*rowNumber;

    try {
      Vec tVec = Vec.makeZero(rowNumber);
      Vec v;
      if (forConstants)
        v = new MRTask() {
          @Override
          public void map(Chunk cs) {
            for (int r = 0; r < cs._len; r++) {
              cs.set(r, baseD);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];
      else
        v = new MRTask() {
          @Override
          public void map(Chunk cs) {
            long rowStart = cs.start();
            for (int r = 0; r < cs._len; r++) {
              cs.set(r, r + baseD + rowStart);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];

      Scope.track(tVec);
      Scope.track(v);

      for (int rowInd=0; rowInd<rowNumber; rowInd=rowInd+rowInterval) {
        if (forConstants)
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (rowInd + baseD) + " v.at(rowIndex): "
                          + v.at8(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  v.at8(rowInd) == baseD);
        else
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (rowInd + baseD) + " v.at8(rowIndex): "
                          + v.at8(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  v.at8(rowInd) == (rowInd + baseD));
      }
    } finally {
      Scope.exit();
    }
  }

  public void testsForDoubles(boolean forConstants, boolean bigDouble, boolean forFloat){
    Scope.enter();
    final double baseD = bigDouble?(double) Long.MAX_VALUE+1:(forFloat?1.1:Math.PI);

    try {
      Vec tVec = Vec.makeZero(rowNumber);
      Vec v;
      if (forConstants)
        v = new MRTask() {
          @Override
          public void map(Chunk cs) {
            for (int r = 0; r < cs._len; r++) {
              cs.set(r, baseD);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];
      else
        v = new MRTask() {
          @Override
          public void map(Chunk cs) {
            long rowStart = cs.start();
            for (int r = 0; r < cs._len; r++) {
              cs.set(r, baseD + rowStart + r);
            }
          }
        }.doAll(tVec)._fr.vecs()[0];

      Scope.track(tVec);
      Scope.track(v);

      for (int rowInd = 0; rowInd < rowNumber; rowInd = rowInd + rowInterval) {
        if (forConstants)
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (baseD) + " v.at(rowIndex): "
                          + v.at(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  Math.abs(v.at(rowInd) - baseD)/Math.max(v.at(rowInd), baseD) < tolerance);
        else
          assertTrue("rowIndex: " + rowInd + " rowInd+baseD: " + (baseD) + " v.at(rowIndex): "
                          + v.at(rowInd) + " chk= " + v.elem2ChunkIdx(rowInd),
                  (Math.abs(v.at(rowInd) - (baseD + rowInd)))/Math.max(v.at(rowInd), (baseD+rowInd)) < tolerance);
      }
    } finally {
      Scope.exit();
    }
  }
}