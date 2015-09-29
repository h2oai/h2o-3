package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.util.Log;
import water.util.PrettyPrint;

public class ChunkSpeedTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  final int cols = 10;
  final int rows = 1000000;
  final int rep = 10;
  double[][] raw = new double[cols][rows];
  Chunk[] chunks = new Chunk[cols];

  @Test
  public void run() {
    for (int j = 0; j < cols; ++j) {
      for (int i = 0; i < rows; ++i) {
//        switch (j%1+1) { //just do 1 byte chunks
//        switch (j % 2) { //just do 1/2 byte chunks
        switch (j%3) { // do 3 chunk types
//        switch (j%4) { // do 4 chunk types
          case 0:
            raw[j][i] = i % 200; //C1NChunk - 1 byte integer
            break;
          case 1:
            raw[j][i] = i % 500; //C2Chunk - 2 byte integer
            break;
          case 2:
            raw[j][i] = i*Integer.MAX_VALUE;
            break;
          case 3:
            raw[j][i] = i == 17 ? 1 : 0; //CX0Chunk - sparse
            break;
        }
      }
    }
    for (int j = 0; j < cols; ++j) {
      chunks[j] = new NewChunk(raw[j]).compress();
      Log.info("Column " + j + " compressed into: " + chunks[j].getClass().toString());
    }

    raw();
    rawInverted();

    chunks();
    chunksInverted();

    chunks3();

    bulk();
  }

  void raw()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int j=0; j<cols; ++j) {
        for (int i = 0; i < rows; ++i) {
          sum += raw[j][i];
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    Log.info("Data size: " + PrettyPrint.bytes(rows * cols * 8));
    Log.info("Time to access raw double[]: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void rawInverted()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int i = 0; i < rows; ++i) {
        for (int j=0; j<cols; ++j) {
          sum += raw[j][i];
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    Log.info("Data size: " + PrettyPrint.bytes(rows * cols * 8));
    Log.info("Time to access raw double[] inverted: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  double walkChunk(final Chunk c) {
    double sum =0;
    for (int i = 0; i < rows; ++i) {
      sum += c.atd(i);
    }
    return sum;
  }

  double loop() {
    double sum =0;
    for (int j=0; j<cols; ++j) {
      sum += walkChunk(chunks[j]);
    }
    return sum;
  }

  double loop3(Chunk c1, Chunk c2, Chunk c3) {
    double sum =0;
    sum += walkChunk(c1);
    sum += walkChunk(c2);
    sum += walkChunk(c3);
    return sum;
  }

  void chunks()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop();
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time to access via atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void chunks3()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop3(chunks[0], chunks[1], chunks[2]);
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time to access via atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void chunksInverted()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int i = 0; i < rows; ++i) {
        for (int j=0; j<cols; ++j) {
          sum += chunks[j].atd(i);
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time to access via inverted atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void bulk()
  {
    long start = 0;
    double sum = 0;
    double[] bulk = new double[rows]; //allocate only once
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int j=0; j<cols; ++j) {
        chunks[j].toDoubleArray(bulk);
        for (int i = 0; i < rows; ++i) {
          sum += bulk[i];
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time to access via bulk reader: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  public static void main(String[] args) {
    setup();
    new ChunkSpeedTest().run();
  }
}

