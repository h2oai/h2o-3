package water.rapids.ast.prims.statistics;

import water.DKV;
import water.MRTask;
import water.Value;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;
import water.util.VecUtils;

import java.util.Objects;

/**
 * Pearson's correlation coefficient (PCC) calculation
 *
 * Note: If one of the observation's columns used to calculate Pearson's corr. coef. contains a NaN value,
 * the whole line is skipped.
 */
public class AstPearson extends AstPrimitive {
  @Override
  public int nargs() {
    return 1 + 3; // Frame ID and ID of two numerical vectors to calculate PCC on.
  }

  @Override
  public String[] args() {
    return new String[0];
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    final Value value = DKV.get(asts[1].str());
    if (!value.isFrame()) {
      throw new IllegalArgumentException(String.format("The given key '%s' is not a frame.", asts[1]));
    }

    final Frame frame = value.get(Frame.class);
    final Vec x = getVectorFromFrame(frame, asts[2].str());
    final Vec y = getVectorFromFrame(frame, asts[3].str());
    // Can't use means from rollup stats - lines with NAs in the columns evaluated are skipped completely. 
    final double[] means = VecUtils.calculateMeansIgnoringNas(x, y);

    final PearsonCorrelationCoefficientTask pearsonTask = new PearsonCorrelationCoefficientTask(means[0], means[1])
            .doAll(x, y);
    return new ValNum(pearsonTask.get_pearsonCorrelationCoefficient());
  }

  /**
   * Retrieves a vector from a {@link Frame} or throws {@link IllegalArgumentException} if not found.
   *
   * @param frame            Frame to obtain vector from
   * @param userGivenColName Column name to calculate PCC from, obtained from user's input
   * @return An instance of {@link Vec} from the {@link Frame} given, if exists. Otherwise an exception is thrown.
   * @throws IllegalArgumentException Thrown if the given column name is not to be found in the {@link Frame}.
   */
  private Vec getVectorFromFrame(final Frame frame, final String userGivenColName) throws IllegalArgumentException {
    Objects.requireNonNull(frame, "Frame must not be null.");
    Objects.requireNonNull(userGivenColName, "Column name to calculate Pearson's cor. coef. from must not be null.");

    final Vec vec = frame.vec(userGivenColName);
    if (vec == null) {
      throw new IllegalArgumentException(String.format("No column found for given name '%s'. Unable to calculate Pearson's corr. coef.",
              userGivenColName));
    }

    return vec;
  }
  @Override
  public String str() {
    return "pearson";
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
  private static class PearsonCorrelationCoefficientTask extends MRTask<PearsonCorrelationCoefficientTask> {

    // Arguments obtained externally
    private final double _xMean;
    private final double _yMean;

    private double _pearsonCorrelationCoefficient;

    // Required to later finish calculation of standard deviation
    private double _xDiffSquared = 0;
    private double _yDiffSquared = 0;
    private double _xyAvgDiffMul = 0;
    // If at least one of the vectors contains NaN, such line is skipped
    private long _linesVisited;

    /**
     * @param xMean Mean value of the first 'x' vector, with NaNs skipped
     * @param yMean Mean value of the second 'y' vector, with NaNs skipped
     */
    private PearsonCorrelationCoefficientTask(final double xMean, final double yMean) {
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
        if (Double.isNaN(x) || Double.isNaN(y)) {
          continue; // Skip NaN values
        }
        _linesVisited++;

        final double xDiffFromMean = x - _xMean;
        final double yDiffFromMean = y - _yMean;
        _xyAvgDiffMul += xDiffFromMean * yDiffFromMean;

        _xDiffSquared += Math.pow(xDiffFromMean, 2);
        _yDiffSquared += Math.pow(yDiffFromMean, 2);
      }
    }


    @Override
    public void reduce(final PearsonCorrelationCoefficientTask mrt) {
      // The intermediate results are addable. The final calculations are done afterwards.
      this._xDiffSquared += mrt._xDiffSquared;
      this._yDiffSquared += mrt._yDiffSquared;
      this._xyAvgDiffMul += mrt._xyAvgDiffMul;
      this._linesVisited += mrt._linesVisited;
    }

    @Override
    protected void postGlobal() {
      // X Standard deviation
      final double xStdDev = Math.sqrt(1D / (_linesVisited - 1) * _xDiffSquared);

      // Y Standard deviation
      final double yStdDev = Math.sqrt(1D / (_linesVisited - 1) * _yDiffSquared);

      _pearsonCorrelationCoefficient = (_xyAvgDiffMul)
              / ((_linesVisited - 1) * xStdDev * yStdDev);
    }

    public double get_pearsonCorrelationCoefficient() {
      return _pearsonCorrelationCoefficient;
    }
  }

}
