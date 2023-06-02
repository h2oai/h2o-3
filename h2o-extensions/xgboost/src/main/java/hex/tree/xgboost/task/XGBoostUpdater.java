package hex.tree.xgboost.task;

import hex.tree.xgboost.BoosterParms;
import ai.h2o.xgboost4j.java.*;
import hex.tree.xgboost.EvalMetric;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class XGBoostUpdater extends Thread {

  private static final Logger LOG = Logger.getLogger(XGBoostUpdater.class);

  private static final long WORK_START_TIMEOUT_SECS = 5 * 60; // Each Booster task should start before this timer expires
  private static final long INACTIVE_CHECK_INTERVAL_SECS = 60;

  private static final NonBlockingHashMap<Key, XGBoostUpdater> updaters = new NonBlockingHashMap<>();

  private final Key _modelKey;
  private final DMatrix _trainMat;
  private final DMatrix _validMat;
  private final BoosterParms _boosterParms;
  private final String _evalMetricSpec;
  private final byte[] _checkpointBoosterBytes;
  private final Map<String, String> _rabitEnv;

  private volatile SynchronousQueue<BoosterCallable<?>> _in;
  private volatile SynchronousQueue<Object> _out;

  private BoosterWrapper _booster;
  private volatile EvalMetric _evalMetric;

  private XGBoostUpdater(
      Key modelKey, DMatrix trainMat, DMatrix validMat, BoosterParms boosterParms, 
      byte[] checkpointBoosterBytes, Map<String, String> rabitEnv
  ) {
    super("XGBoostUpdater-" + modelKey);
    _modelKey = modelKey;
    _trainMat = trainMat;
    _validMat = validMat;
    _boosterParms = boosterParms;
    _checkpointBoosterBytes = checkpointBoosterBytes;
    _rabitEnv = rabitEnv;
    _evalMetricSpec = (String) _boosterParms.get().get("eval_metric");
    _in = new SynchronousQueue<>();
    _out = new SynchronousQueue<>();
  }

  @Override
  public void run() {
    try {
      Rabit.init(_rabitEnv);

      while (! interrupted()) {
        BoosterCallable<?> task = _in.take();
        Object result = task.call();
        _out.put(result);
      }
    } catch (InterruptedException e) {
      XGBoostUpdater self = updaters.get(_modelKey);
      if (self != null) {
        LOG.error("Updater thread was interrupted while it was still registered, name=" + self.getName(), e);
      } else {
        LOG.debug("Updater thread interrupted.", e);
      }
      Thread.currentThread().interrupt();
    } catch (XGBoostError e) {
      LOG.error("XGBoost training iteration failed", e);
    } finally {
      _in = null; // Will throw NPE if used wrong
      _out = null;
      updaters.remove(_modelKey);
      try {
        _trainMat.dispose();
        if (_validMat != null) {
          _validMat.dispose();
        }
        if (_booster != null)
          _booster.dispose();
      } catch (Exception e) {
        LOG.warn("Failed to dispose of training matrix/booster", e);
      }
      try {
        Rabit.shutdown();
      } catch (Exception xgBoostError) {
        LOG.warn("Rabit shutdown during update failed", xgBoostError);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T invoke(BoosterCallable<T> callable) throws InterruptedException {
    final SynchronousQueue<BoosterCallable<?>> inQ = _in;
    if (inQ == null)
      throw new IllegalStateException("Updater is inactive on node " + H2O.SELF);
    if (! inQ.offer(callable, WORK_START_TIMEOUT_SECS, TimeUnit.SECONDS))
      throw new IllegalStateException("XGBoostUpdater couldn't start work on task " + callable + " in "  + WORK_START_TIMEOUT_SECS + "s.");
    SynchronousQueue<?> outQ;
    int i = 0;
    while ((outQ = _out) != null) {
      i++;
      T result = (T) outQ.poll(INACTIVE_CHECK_INTERVAL_SECS, TimeUnit.SECONDS);
      if (result != null) {
        return result;
      } else if (i > 5) {
        LOG.warn(String.format("XGBoost task of type '%s' is taking unexpectedly long, it didn't finish in %d seconds.",
                callable, INACTIVE_CHECK_INTERVAL_SECS * i));
      }
    }
    throw new IllegalStateException("Cannot perform booster operation: updater is inactive on node " + H2O.SELF);
  }

  private class UpdateBooster implements BoosterCallable<Booster> {
    private final int _tid;

    private UpdateBooster(int tid) { _tid = tid; }

    @Override
    public Booster call() throws XGBoostError {
      if ((_booster == null) && _tid == 0) {
        _booster = new BoosterWrapper(_checkpointBoosterBytes, _boosterParms.get(), _trainMat, _validMat);
        _evalMetric = computeEvalMetric();
        // Force Booster initialization; we can call any method that does "lazy init"
        byte[] boosterBytes = _booster.toByteArray();
        LOG.info("Initial Booster created, size=" + boosterBytes.length);
      } else {
        // Do one iteration
        assert _booster != null;
        _booster.update(_trainMat, _tid);
        _evalMetric = computeEvalMetric();
        _booster.saveRabitCheckpoint();
      }
      return _booster.getBooster();
    }

    private EvalMetric computeEvalMetric() throws XGBoostError {
      if (_evalMetricSpec == null) {
        return null;
      }
      final String evalMetricVal = _booster.evalSet(_trainMat, _validMat, _tid);
      return parseEvalMetric(evalMetricVal);
    }
    
    @Override
    public String toString() {
      return "Boosting Iteration (tid=" + _tid + ")";
    }
  }

  private EvalMetric parseEvalMetric(String evalMetricVal) {
    return parseEvalMetric(_evalMetricSpec, _validMat != null, evalMetricVal);
  }
  
  static EvalMetric parseEvalMetric(String evalMetricSpec, boolean hasValid, String evalMetricVal) {
    final String[] parts = evalMetricVal.split("\t");
    final int expectedParts = hasValid ? 3 : 2;
    if (parts.length != expectedParts) {
      Log.err("Evaluation metric cannot be parsed, unexpected number of elements. Value: '" + evalMetricSpec + "'.");
      return EvalMetric.empty(evalMetricSpec);
    }
    double trainVal, validVal = Double.NaN;
    trainVal = parseEvalMetricPart(parts[1]);
    if (hasValid) {
      validVal = parseEvalMetricPart(parts[2]);
    }
    return new EvalMetric(evalMetricSpec, trainVal, validVal);
  }

  static double parseEvalMetricPart(String evalMetricVal) {
    final int sepPos = evalMetricVal.lastIndexOf(":");
    if (sepPos >= 0) {
      String valStr = evalMetricVal.substring(sepPos + 1).trim();
      try {
        return Double.parseDouble(valStr);
      } catch (Exception e) {
        Log.err("Failed to parse value of evaluation metric: '" + evalMetricVal + "'.", e);
      }
    }
    return Double.NaN;
  }
  
  private class SerializeBooster implements BoosterCallable<byte[]> {
    @Override
    public byte[] call() throws XGBoostError {
      return _booster.toByteArray();
    }
    @Override
    public String toString() {
      return "SerializeBooster";
    }
  }

  byte[] getBoosterBytes() {
    try {
      return invoke(new SerializeBooster());
    } catch (InterruptedException e) {
      throw new IllegalStateException("Failed to serialize Booster - operation was interrupted", e);
    }
  }

  EvalMetric getEvalMetric() {
    return _evalMetric;
  }

  Booster doUpdate(int tid) {
    try {
      return invoke(new UpdateBooster(tid));
    } catch (InterruptedException e) {
      throw new IllegalStateException("Boosting iteration failed - operation was interrupted", e);
    }
  }

  static XGBoostUpdater make(Key modelKey, DMatrix trainMat, DMatrix validMat, BoosterParms boosterParms,
                             byte[] checkpoint, Map<String, String> rabitEnv) {
    XGBoostUpdater updater = new XGBoostUpdater(modelKey, trainMat, validMat, boosterParms, checkpoint, rabitEnv);
    updater.setUncaughtExceptionHandler(LoggingExceptionHandler.INSTANCE);
    if (updaters.putIfAbsent(modelKey, updater) != null)
      throw new IllegalStateException("XGBoostUpdater for modelKey=" + modelKey + " already exists!");
    return updater;
  }

  static void terminate(Key modelKey) {
    XGBoostUpdater updater = updaters.remove(modelKey);
    if (updater == null)
      LOG.debug("XGBoostUpdater for modelKey=" + modelKey + " was already clean-up on node " + H2O.SELF);
    else
      updater.interrupt();
  }

  static XGBoostUpdater getUpdater(Key modelKey) {
    XGBoostUpdater updater = updaters.get(modelKey);
    if (updater == null) {
      throw new IllegalStateException("XGBoostUpdater for modelKey=" + modelKey + " was not found!");
    }
    return updater;
  }

  private interface BoosterCallable<E> {
    E call() throws XGBoostError;
  }

  private static class LoggingExceptionHandler implements UncaughtExceptionHandler {
    private static LoggingExceptionHandler INSTANCE = new LoggingExceptionHandler();
    @Override
    public void uncaughtException(Thread t, Throwable e) {
      LOG.error("Uncaught exception in " + t.getName(), e);
    }
  }
  
}
