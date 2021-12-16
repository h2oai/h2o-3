package hex.Infogram;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;

public class EstimateCMI extends MRTask<EstimateCMI> {
  public int _nonZeroRows;
  public double _accumulatedCMI;
  public double _meanCMI;
  public static final double _scale = 1.0/Math.log(2);
  public final int _responseColumn;

  public EstimateCMI(Frame fr) {
    _meanCMI = 0.0;
    _responseColumn = fr.numCols()-1;
  }

  @Override
  public void map(Chunk[] ck) {
    _nonZeroRows = 0;
    _accumulatedCMI = 0.0;
    int numRow = ck[0].len();
    for (int rowIndex = 0; rowIndex < numRow; rowIndex++) {
      int prediction = (int) ck[_responseColumn].atd(rowIndex);
      double predictionProb = ck[prediction+1].atd(rowIndex);
      if (!Double.isNaN(predictionProb) && predictionProb > 0) {
        _nonZeroRows++;
        _accumulatedCMI += Math.log(predictionProb);
      }
    }
  }

  @Override
  public void reduce(EstimateCMI other) {
    _nonZeroRows += other._nonZeroRows;
    _accumulatedCMI += other._accumulatedCMI;
  }

  @Override
  public void postGlobal() {
    _meanCMI = _scale*_accumulatedCMI/_nonZeroRows;
  }
}
