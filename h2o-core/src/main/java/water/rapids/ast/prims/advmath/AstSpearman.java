package water.rapids.ast.prims.advmath;

import water.DKV;
import water.MRTask;
import water.Scope;
import water.Value;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;
import water.util.FrameUtils;

public class AstSpearman extends AstPrimitive<AstSpearman> {
  @Override
  public int nargs() {
    return 1 + 3; // Frame ID and ID of two numerical vectors to calculate SCC on.
  }

  @Override
  public String[] args() {
    return new String[]{"frame", "first_column", "second_column"};
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    final Value value = DKV.get(asts[1].str());
    if (!value.isFrame()) {
      throw new IllegalArgumentException(String.format("The given key '%s' is not a frame.", asts[1]));
    }

    final Frame originalUnsortedFrame = value.get(Frame.class);
    final int vecIdX = originalUnsortedFrame.find(asts[2].exec(env).getStr());
    final int vecIdY = originalUnsortedFrame.find(asts[3].exec(env).getStr());

    try {
      Scope.enter();
      final PearsonRankedVectors rankedVectors = rankedVectors(originalUnsortedFrame, vecIdX, vecIdY);
      final SpearmanCorrelationCoefficientTask spearman = new SpearmanCorrelationCoefficientTask(rankedVectors._x.mean(),
              rankedVectors._y.mean())
              .doAll(rankedVectors._x, rankedVectors._y);
      return new ValNum(spearman.getSpearmanCorrelationCoefficient());
    } finally {
      Scope.exit();
    }

  }

  /**
   * Sorts and ranks the vectors of which SCC is calculated. Original Frame is not modified.
   *
   * @param originalUnsortedFrame Original frame containing the vectors compared.
   * @param vecIdX                First compared vector
   * @param vecIdY                Second compared vector
   * @return An instance of {@link PearsonRankedVectors}, holding two new vectors with row rank.
   */
  private PearsonRankedVectors rankedVectors(final Frame originalUnsortedFrame, final int vecIdX, final int vecIdY) {
    Frame sortedX = new Frame(originalUnsortedFrame.vec(vecIdX).makeCopy());
    Scope.track(sortedX);
    Frame sortedY = new Frame(originalUnsortedFrame.vec(vecIdY).makeCopy());
    Scope.track(sortedY);

    if (!sortedX.vec(0).isCategorical()) {
      FrameUtils.labelRows(sortedX, "label");
      sortedX = sortedX.sort(new int[]{0});
      Scope.track(sortedX);

    }

    if (!sortedY.vec(0).isCategorical()) {
      FrameUtils.labelRows(sortedY, "label");
      sortedY = sortedY.sort(new int[]{0});
      Scope.track(sortedY);
    }

    assert sortedX.numRows() == sortedY.numRows();
    final Vec orderX = Vec.makeZero(sortedX.numRows());
    final Vec orderY = Vec.makeZero(sortedY.numRows());

    final Vec xLabel = sortedX.vec("label") == null ? sortedX.vec(0) : sortedX.vec("label");
    final Vec xValue = sortedX.vec(0);
    final Vec yLabel = sortedY.vec("label") == null ? sortedY.vec(0) : sortedY.vec("label");
    final Vec yValue = sortedY.vec(0);
    Scope.track(xLabel);
    Scope.track(yLabel);


    final Vec.Writer orderXWriter = orderX.open();
    final Vec.Writer orderYWriter = orderY.open();
    final Vec.Reader xValueReader = xValue.new Reader();
    final Vec.Reader yValueReader = yValue.new Reader();
    final Vec.Reader xLabelReader = xLabel.new Reader();
    final Vec.Reader yLabelReader = yLabel.new Reader();

    double lastX = Double.NaN;
    double lastY = Double.NaN;
    long skippedX = 0;
    long skippedY = 0;
    for (int i = 0; i < orderX.length(); i++) {
      if (lastX == xValueReader.at(i)) {
        skippedX++;
      } else {
        skippedX = 0;
      }
      lastX = xValueReader.at(i);
      orderXWriter.set(xLabelReader.at8(i) - 1, i - skippedX);

      if (lastY == yValueReader.at(i)) {
        skippedY++;
      } else {
        skippedY = 0;
      }
      lastY = yValueReader.at(i);
      orderYWriter.set(yLabelReader.at8(i) - 1, i - skippedY);
    }
    orderXWriter.close();
    orderYWriter.close();

    return new PearsonRankedVectors(orderX, orderY);
  }

  /**
   * Ranked vectors prepared to calculate Spearman's correlation coefficient
   */
  private static class PearsonRankedVectors {
    private final Vec _x;
    private final Vec _y;

    public PearsonRankedVectors(Vec x, Vec y) {
      this._x = x;
      this._y = y;
    }
  }


  @Override
  public String str() {
    return "spearman";
  }

  /**
   * A task to do calculate Pearson's correlation coefficient.
   * The intermediate calculations required for standard deviant of both columns could be calculated by existing code,
   * however the point is to perform the calculations by going through the data only once.
   * <p>
   * Note: If one of the observation's columns used to calculate Pearson's corr. coef. contains a NaN value,
   * the whole line is skipped.
   *
   * @see {@link water.rapids.ast.prims.advmath.AstVariance}
   */
  private static class SpearmanCorrelationCoefficientTask extends MRTask<SpearmanCorrelationCoefficientTask> {
    // Arguments obtained externally
    private final double _xMean;
    private final double _yMean;

    private double spearmanCorrelationCoefficient;

    // Required to later finish calculation of standard deviation
    private double _xDiffSquared = 0;
    private double _yDiffSquared = 0;
    private double _xyMul = 0;
    // If at least one of the vectors contains NaN, such line is skipped
    private long _linesVisited;
    
    /**
     * @param xMean Mean value of the first 'x' vector, with NaNs skipped
     * @param yMean Mean value of the second 'y' vector, with NaNs skipped
     */
    private SpearmanCorrelationCoefficientTask(final double xMean, final double yMean) {
      this._xMean = xMean;
      this._yMean = yMean;
    }

    @Override
    public void map(Chunk[] chunks) {
      assert chunks.length == 2; // Amount of linear correlation only calculated between two vectors at once
      final Chunk xChunk = chunks[0];
      final Chunk yChunk = chunks[1];

      for (int row = 0; row < chunks[0].len(); row++) {
        final double x = xChunk.atd(row);
        final double y = yChunk.atd(row);
        _linesVisited++;

        _xyMul += x * y;
        
        final double xDiffFromMean = x - _xMean;
        final double yDiffFromMean = y - _yMean;
        _xDiffSquared += Math.pow(xDiffFromMean, 2);
        _yDiffSquared += Math.pow(yDiffFromMean, 2);
      }
    }


    @Override
    public void reduce(final SpearmanCorrelationCoefficientTask mrt) {
      // The intermediate results are addable. The final calculations are done afterwards.
      this._xDiffSquared += mrt._xDiffSquared;
      this._yDiffSquared += mrt._yDiffSquared;
      this._linesVisited += mrt._linesVisited;
      this._xyMul += mrt._xyMul;
    }

    @Override
    protected void postGlobal() {
      final double xStdDev = Math.sqrt(_xDiffSquared / _linesVisited);
      final double yStdDev = Math.sqrt(_yDiffSquared / _linesVisited);

      spearmanCorrelationCoefficient = (_xyMul - (_linesVisited * _xMean * _yMean))
              / ((_linesVisited) * xStdDev * yStdDev);
    }

    public double getSpearmanCorrelationCoefficient() {
      return spearmanCorrelationCoefficient;
    }
  }
}
