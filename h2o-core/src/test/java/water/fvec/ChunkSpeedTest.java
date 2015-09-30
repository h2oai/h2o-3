package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.MRTask;
import water.TestUtil;
import water.util.Log;
import water.util.PrettyPrint;

public class ChunkSpeedTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  final int cols = 100;
  final int rows = 50000;
  final int rep = 20;
  final double[][] raw = new double[cols][rows];
  Chunk[] chunks = new Chunk[cols];

  @Test
  public void run() {
    for (int j = 0; j < cols; ++j) {
      for (int i = 0; i < rows; ++i) {
        raw[j][i] = get(j,i);
      }
    }
    for (int j = 0; j < cols; ++j) {
      chunks[j] = new NewChunk(raw[j]).compress();
      Log.info("Column " + j + " compressed into: " + chunks[j].getClass().toString());
    }
    Log.info("COLS: " + cols);
    Log.info("ROWS: " + rows);
    Log.info("REPS: " + rep);

    raw();
    raw();

    chunks();
    chunks();

    chunksInline();
    chunksInline();

    mrtask(false);
    mrtask(false);

    bulk();
    bulk();

    Log.info("Now doing funny stuff.\n\n");
    mrtask(true);
    mrtask(true);

//    raw();
//    raw();

    chunksInverted();
    chunksInverted();

    rawInverted();
    rawInverted();

  }

  double get(int j, int i) {
//        switch (j%1+1) { //just do 1 byte chunks
//        switch (j % 2) { //just do 1/2 byte chunks
    switch (j%3) { // do 3 chunk types
//        switch (j%4) { // do 4 chunk types
      case 0:
        return i % 200; //C1NChunk - 1 byte integer
      case 1:
        return i % 500; //C2Chunk - 2 byte integer
      case 2:
        return  i*Integer.MAX_VALUE;
      case 3:
        return i == 17 ? 1 : 0; //CX0Chunk - sparse
      default:
        throw H2O.unimpl();
    }
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
    Log.info("Time for RAW double[]: " + PrettyPrint.msecs(done - start, true));
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
    Log.info("Time for INVERTED RAW double[]: " + PrettyPrint.msecs(done - start, true));
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

  void chunksInline()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int j=0; j<cols; ++j) {
        for (int i = 0; i < rows; ++i) {
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
    Log.info("Time for INLINE chunks atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
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
    Log.info("Time for METHODS chunks atd(): " + PrettyPrint.msecs(done - start, true));
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
    Log.info("Time for INVERTED INLINE chunks atd(): " + PrettyPrint.msecs(done - start, true));
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
    Log.info("Time for bulk reader: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  class FillTask extends MRTask<FillTask> {
    @Override
    public void map(Chunk[] cs) {
      for (int col=0; col<cs.length; ++col) {
        for (int row=0; row<cs[0]._len; ++row) {
          cs[col].set(row, raw[col][row]);
        }
      }
    }
  }

  static class SumTask extends MRTask<SumTask> {
    double _sum;
    @Override
    public void map(Chunk[] cs) {
      for (int col=0; col<cs.length; ++col) {
        for (int row=0; row<cs[0]._len; ++row) {
          _sum += cs[col].atd(row);
        }
      }
    }
    @Override
    public void reduce(SumTask other) {
      _sum += other._sum;
    }
  }

  void mrtask(boolean parallel)
  {
    long start = 0;
    double sum = 0;
    Frame fr = new Frame();
    for (int i=0; i<cols; ++i) {
      if (parallel)
        fr.add("C" + i, Vec.makeCon(0, rows)); //multi-chunk (based on #cores)
      else
        fr.add("C"+i, Vec.makeVec(raw[i], Vec.newKey())); //directly fill from raw double array (1 chunk)
    }
    if (parallel) new FillTask().doAll(fr);

    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += new SumTask().doAll(fr)._sum;
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    siz += fr.byteSize();
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for " + (parallel ? "PARALLEL":"SERIAL") + " MRTask: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
    fr.remove();
  }

  public static void main(String[] args) {
    setup();
    new ChunkSpeedTest().run();
  }
}

