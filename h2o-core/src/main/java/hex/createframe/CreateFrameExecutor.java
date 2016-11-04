package hex.createframe;

import water.*;
import water.fvec.*;
import water.util.RandomUtils;

import java.util.ArrayList;
import java.util.Random;


/**
 * This class carries out the frame creation job.
 */
public class CreateFrameExecutor extends H2O.H2OCountedCompleter<CreateFrameExecutor> {
  private Job<Frame> job;
  private Key<Frame> destKey;
  private ArrayList<CreateFrameColumnSpec> columnMakers;
  private ArrayList<CreateFramePostprocessStep> postprocessSteps;
  private float workAmountPerRow;
  private float bytesPerRow;
  private int numRows;
  private int numCols;
  private long seed;

  public CreateFrameExecutor(Job<Frame> job) {
    this.job = job;
    destKey = job._result;
    columnMakers = new ArrayList<>(10);
    postprocessSteps = new ArrayList<>(2);
  }

  public void setNumRows(int n) {
    numRows = n;
  }

  public void setSeed(long s) {
    seed = s;
  }

  public void addColumnMaker(CreateFrameColumnSpec maker) {
    maker.setIndex(numCols);
    columnMakers.add(maker);
    workAmountPerRow += maker.workAmount();
    bytesPerRow += maker.byteSizePerRow();
    numCols += maker.numColumns();
  }

  public void addPostprocessStep(CreateFramePostprocessStep step) {
    postprocessSteps.add(step);
  }

  public int workAmount() {
    return (int)(numRows * workAmountPerRow);
  }

  public long estimatedByteSize() {
    return (long)(numRows * bytesPerRow);
  }

  @Override public void compute2() {
    int logRowsPerChunk = (int) Math.ceil(Math.log1p(rowsPerChunk()));
    Vec dummyVec = Vec.makeCon(0, numRows, logRowsPerChunk, false);

    // Create types, names & domains
    byte[] types = new byte[numCols];
    String[] names = new String[numCols];
    String[][] domains = new String[numCols][];
    int i = 0;
    for (CreateFrameColumnSpec maker : columnMakers) {
      int it = 0, in = 0, id = 0;
      for (byte t : maker.columnTypes()) types[i + it++] = t;
      for (String n : maker.columnNames()) names[i + in++] = n;
      String[][] colDomains = maker.columnDomains();
      if (colDomains != null) {
        for (String[] d : colDomains)
          domains[i + id++] = d;
      } // otherwise don't do anything and leave those entries in `domains` as nulls.
      assert in == it && (id == it || id == 0) && it == maker.numColumns();
      i += it;
    }

    // Make the frame
    Frame out = new ActualFrameCreator(columnMakers, seed, job)
        .doAll(types, dummyVec)
        .outputFrame(destKey, names, domains);

    // Post-process the frame
    Random rng = RandomUtils.getRNG(seed + 40245345791L);
    rng.setSeed(rng.nextLong());
    for (CreateFramePostprocessStep step: postprocessSteps) {
      long nextSeed = rng.nextLong();
      step.exec(out, rng);
      rng.setSeed(nextSeed);
    }

    // Clean up
    DKV.put(out);
    dummyVec.remove();
    tryComplete();
  }

  private int rowsPerChunk() {
    return FileVec.calcOptimalChunkSize(
        estimatedByteSize(),
        numCols,
        numCols * 4,
        Runtime.getRuntime().availableProcessors(),
        H2O.getCloudSize(),
        false,
        false
    );
  }

  private static class ActualFrameCreator extends MRTask<ActualFrameCreator> {
    private long seed;
    private ArrayList<CreateFrameColumnSpec> columnMakers;
    private Job<Frame> job;

    public ActualFrameCreator(ArrayList<CreateFrameColumnSpec> columnMakers, long seed, Job<Frame> job) {
      this.columnMakers = columnMakers;
      this.seed = seed;
      this.job = job;
    }

    @Override public void map(Chunk[] cs, NewChunk[] ncs) {
      if (job.stop_requested()) return;
      int numRowsInChunk = cs[0]._len;
      long chunkPosition = cs[0].start();
      Random rng = RandomUtils.getRNG(0);
      long taskIndex = 0;
      for (CreateFrameColumnSpec colTask : columnMakers) {
        rng.setSeed(seed + chunkPosition * 138457623L + (taskIndex++) * 967058L);
        rng.setSeed(rng.nextLong());
        colTask.exec(numRowsInChunk, ncs, rng);
        job.update(colTask.workAmount());
      }
    }
  }
}
