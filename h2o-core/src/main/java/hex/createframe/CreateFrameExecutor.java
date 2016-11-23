package hex.createframe;

import water.*;
import water.fvec.*;
import water.util.RandomUtils;

import java.util.ArrayList;
import java.util.Random;


/**
 * <p>This class carries out the frame creation job.</p>
 *
 * <p>Frame creation is conceptually done in 3 stages: First, a build "recipe"
 * is prepared. This recipe is the detailed specification of how the frame is
 * to be constructed. Second, an MRTask is run that actually creates the frame,
 * according to the specification in the recipe. In this step all "column
 * makers" are executed in order they were added, for each chunk-row being
 * created. Finally, a set of postprocessing steps are performed on the
 * resulting frame.</p>
 *
 * <p>Usage example:
 * <pre>{@code
 *   Job<Frame> job = new Job<>(destination_key, Frame.class.getName(), "CreateFrame");
 *   CreateFrameExecutor cfe = new CreateFrameExecutor(job);
 *   cfe.setNumRows(10000);
 *   cfe.setSeed(0xDECAFC0FEE);
 *   cfe.addColumnMaker(new RealColumnCfcm("col0", -1, 1));
 *   cfe.addColumnMaker(new IntegerColumnCfcm("col1", 0, 100));
 *   cfe.addPostprocessStep(new MissingInserterCfps(0.05));
 *   job.start(cfe, cfe.workAmount());
 * }</pre></p>
 */
public class CreateFrameExecutor extends H2O.H2OCountedCompleter<CreateFrameExecutor> {
  private Job<Frame> job;
  private ArrayList<CreateFrameColumnMaker> columnMakers;
  private ArrayList<CreateFramePostprocessStep> postprocessSteps;
  private int workAmountPerRow;
  private int workAmountPostprocess;
  private float bytesPerRow;
  private int numRows;
  private int numCols;
  private long seed;

  /**
   * Make a new CreateFrameExecutor.
   * @param job The {@link Job} instance which is wrapping this executor. This
   *            instance will be used to update it with the current task
   *            progress.
   */
  public CreateFrameExecutor(Job<Frame> job) {
    this.job = job;
    columnMakers = new ArrayList<>(10);
    postprocessSteps = new ArrayList<>(2);
    seed = -1;
  }

  /**
   * Set number of rows to be created in the resulting frame. (However a
   * postprocess step may remove some of the rows).
   */
  public void setNumRows(int n) {
    numRows = n;
  }

  /**
   * Set the seed for the random number generator. Two frames created from the
   * same seed will be identical. Seed value of -1 (the default) means that a
   * random seed will be issued.
   */
  public void setSeed(long s) {
    seed = s;
  }

  /**
   * Add a "column maker" task, responsible for creation of a single (rarely
   * married or widowed) column.
   */
  public void addColumnMaker(CreateFrameColumnMaker maker) {
    maker.setIndex(numCols);
    columnMakers.add(maker);
    workAmountPerRow += maker.workAmount();
    bytesPerRow += maker.byteSizePerRow();
    numCols += maker.numColumns();
  }

  /**
   * Add a step to be performed in the end after the frame has been created.
   * This step can then modify the frame in any way.
   */
  public void addPostprocessStep(CreateFramePostprocessStep step) {
    postprocessSteps.add(step);
    workAmountPostprocess += step.workAmount();
  }

  /**
   * Return total amount of work that will be performed by the executor. This
   * is needed externally in the Job execution context to determine the
   * progress if the task if it is long-running.
   */
  public int workAmount() {
    return numRows * workAmountPerRow + workAmountPostprocess;
  }

  /**
   * Estimated size of the frame (in bytes), to be used in determining the
   * optimal chunk size. This estimate may not be absolutely precise.
   */
  public long estimatedByteSize() {
    return (long)(numRows * bytesPerRow);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  @Override public void compute2() {
    int logRowsPerChunk = (int) Math.ceil(Math.log1p(rowsPerChunk()));
    Vec dummyVec = Vec.makeCon(0, numRows, logRowsPerChunk, false);
    if (seed == -1)
      seed = Double.doubleToLongBits(Math.random());

    // Create types, names & domains
    byte[] types = new byte[numCols];
    String[] names = new String[numCols];
    String[][] domains = new String[numCols][];
    int i = 0;
    for (CreateFrameColumnMaker maker : columnMakers) {
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
        .outputFrame(job._result, names, domains);

    // Post-process the frame
    Random rng = RandomUtils.getRNG(seed + 40245345791L);
    rng.setSeed(rng.nextLong());
    for (CreateFramePostprocessStep step: postprocessSteps) {
      long nextSeed = rng.nextLong();
      step.exec(out, rng);
      rng.setSeed(nextSeed);
      job.update(step.workAmount());
    }

    // Clean up
    DKV.put(out);
    dummyVec.remove();
    tryComplete();
  }

  /** Compute optimal number of rows per chunk in the resulting frame. */
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
    private ArrayList<CreateFrameColumnMaker> columnMakers;
    private Job<Frame> job;

    public ActualFrameCreator(ArrayList<CreateFrameColumnMaker> columnMakers, long seed, Job<Frame> job) {
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
      for (CreateFrameColumnMaker colTask : columnMakers) {
        rng.setSeed(seed + chunkPosition * 138457623L + (taskIndex++) * 967058L);
        rng.setSeed(rng.nextLong());
        colTask.exec(numRowsInChunk, ncs, rng);
        job.update(colTask.workAmount());
      }
    }
  }
}
