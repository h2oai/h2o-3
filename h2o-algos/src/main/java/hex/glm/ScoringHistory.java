package hex.glm;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;

/**
 * Created by tomas on 9/15/17.
 */
class ScoringHistory {
  private ArrayList<Integer> _scoringIters = new ArrayList<>();
  private ArrayList<Long> _scoringTimes = new ArrayList<>();
  private ArrayList<Double> _likelihoods = new ArrayList<>();
  private ArrayList<Double> _objectives = new ArrayList<>();

  public synchronized void addIterationScore(int iter, double likelihood, double obj) {
    if (_scoringIters.size() > 0 && _scoringIters.get(_scoringIters.size() - 1) == iter)
      return; // do not record twice, happens for the last iteration, need to record scoring history in checkKKTs because of gaussian fam.
    _scoringIters.add(iter);
    _scoringTimes.add(System.currentTimeMillis());
    _likelihoods.add(likelihood);
    _objectives.add(obj);
  }

  public synchronized TwoDimTable to2dTable() {
    String[] cnames = new String[]{"timestamp", "duration", "iteration", "negative_log_likelihood", "objective"};
    String[] ctypes = new String[]{"string", "string", "int", "double", "double"};
    String[] cformats = new String[]{"%s", "%s", "%d", "%.5f", "%.5f"};
    TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_scoringIters.size()], cnames, ctypes, cformats, "");
    int j = 0;
    DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    for (int i = 0; i < _scoringIters.size(); ++i) {
      int col = 0;
      res.set(i, col++, fmt.print(_scoringTimes.get(i)));
      res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
      res.set(i, col++, _scoringIters.get(i));
      res.set(i, col++, _likelihoods.get(i));
      res.set(i, col++, _objectives.get(i));
    }
    return res;
  }

  static class LambdaSearchScoringHistory {
    ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _lambdas = new ArrayList<>();
    private ArrayList<Integer> _lambdaIters = new ArrayList<>();
    private ArrayList<Integer> _lambdaPredictors = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTrain = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTest;
    private ArrayList<Double> _lambdaDevXval;
    private ArrayList<Double> _lambdaDevXvalSE;


    public LambdaSearchScoringHistory(boolean hasTest, boolean hasXval) {
      if(hasTest || true)_lambdaDevTest = new ArrayList<>();
      if(hasXval){
        _lambdaDevXval = new ArrayList<>();
        _lambdaDevXvalSE = new ArrayList<>();
      }
    }

    public synchronized void addLambdaScore(int iter, int predictors, double lambda, double devRatioTrain, double devRatioTest, double devRatioXval, double devRatoioXvalSE) {
      _scoringTimes.add(System.currentTimeMillis());
      _lambdaIters.add(iter);
      _lambdas.add(lambda);
      _lambdaPredictors.add(predictors);
      _lambdaDevTrain.add(devRatioTrain);
      if(_lambdaDevTest != null)_lambdaDevTest.add(devRatioTest);
      if(_lambdaDevXval != null)_lambdaDevXval.add(devRatioXval);
      if(_lambdaDevXvalSE != null)_lambdaDevXvalSE.add(devRatoioXvalSE);
    }
    public synchronized TwoDimTable to2dTable() {

      String[] cnames = new String[]{"timestamp", "duration", "iteration", "lambda", "predictors", "deviance_train"};
      if(_lambdaDevTest != null)
        cnames = ArrayUtils.append(cnames,"deviance_test");
      if(_lambdaDevXval != null)
        cnames = ArrayUtils.append(cnames,new String[]{"deviance_xval","deviance_se"});
      String[] ctypes = new String[]{"string", "string", "int", "string","int", "double"};
      if(_lambdaDevTest != null)
        ctypes = ArrayUtils.append(ctypes,"double");
      if(_lambdaDevXval != null)
        ctypes = ArrayUtils.append(ctypes, new String[]{"double","double"});
      String[] cformats = new String[]{"%s", "%s", "%d","%s", "%d", "%.3f"};
      if(_lambdaDevTest != null)
        cformats = ArrayUtils.append(cformats,"%.3f");
      if(_lambdaDevXval != null)
        cformats = ArrayUtils.append(cformats,new String[]{"%.3f","%.3f"});
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_lambdaIters.size()], cnames, ctypes, cformats, "");
      int j = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      for (int i = 0; i < _lambdaIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, fmt.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _lambdaIters.get(i));
        res.set(i, col++, GLM.lambdaFormatter.format(_lambdas.get(i)));
        res.set(i, col++, _lambdaPredictors.get(i));
        res.set(i, col++, _lambdaDevTrain.get(i));
        if(_lambdaDevTest != null && _lambdaDevTest.size() > i)
          res.set(i, col++, _lambdaDevTest.get(i));
        if(_lambdaDevXval != null && _lambdaDevXval.size() > i) {
          res.set(i, col++, _lambdaDevXval.get(i));
          res.set(i, col++, _lambdaDevXvalSE.get(i));
        }
      }
      return res;
    }
  }
}
