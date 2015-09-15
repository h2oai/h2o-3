package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;

public class ChunkSpeedTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run() {
    final int cols = 100;
    final int rows = 100000;
    final int rep = 10;
    double[][] raw = new double[cols][rows];
    for (int j=0; j<cols; ++j) {
      for (int i = 0; i < rows; ++i) {
//        switch (j%2) { //just do 1/2 byte chunks
        switch (j%3) { // do all 3 chunk types
          case 0:
            raw[j][i] = i % 200; //C1NChunk - 1 byte integer
            break;
          case 1:
            raw[j][i] = i % 500; //C2Chunk - 2 byte integer
            break;
          case 2:
            raw[j][i] = i == 17 ? 1 : 0; //CX0Chunk - sparse
            break;
        }
      }
    }
    Chunk[] chunks = new Chunk[cols];
    for (int j=0; j<cols; ++j) {
      chunks[j] = new NewChunk(raw[j]).compress();
      Log.info("Column " + j + " compressed into: " + chunks[j].getClass().toString());
    }

    // raw data
    {
      long start = 0;
      double sum[] = new double[cols];
      for (int r = 0; r < rep; ++r) {
        if (r==rep/10)
          start = System.currentTimeMillis();
        for (int j=0; j<cols; ++j) {
          for (int i = 0; i < rows; ++i) {
            sum[j] += raw[j][i];
          }
        }
      }
      long done = System.currentTimeMillis();
      Log.info("Sum: " + ArrayUtils.sum(sum));
      Log.info("Data size: " + PrettyPrint.bytes(rows * cols * 8));
      Log.info("Time to access raw double[]: " + PrettyPrint.msecs(done - start, true));
      Log.info("");
    }

    // chunks
    {
      long start = 0;
      double sum[] = new double[cols];
      for (int r = 0; r < rep; ++r) {
        if (r==rep/10)
          start = System.currentTimeMillis();
        for (int j=0; j<cols; ++j) {
          for (int i = 0; i < rows; ++i) {
            sum[j] += chunks[j].atd(i);
          }
        }
      }
      long done = System.currentTimeMillis();
      Log.info("Sum: " + ArrayUtils.sum(sum));
      long siz = 0;
      for (int j=0; j<cols; ++j) {
        siz += chunks[j].byteSize();
      }
      Log.info("Data size: " + PrettyPrint.bytes(siz));
      Log.info("Time to access via atd(): " + PrettyPrint.msecs(done - start, true));
      Log.info("");
    }

  }

}

