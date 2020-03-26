package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import water.*;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class XGBoostUpdater extends Thread {

  private static long WORK_START_TIMEOUT_SECS = 5 * 60; // Each Booster task should start before this timer expires
  private static long INACTIVE_CHECK_INTERVAL_SECS = 60;

  private static final NonBlockingHashMap<Key, XGBoostUpdater> updaters = new NonBlockingHashMap<>();

  private final Key _modelKey;
  private final DMatrix _trainMat;
  private final BoosterParms _boosterParms;
  private final byte[] _checkpointBoosterBytes;
  private final Map<String, String> _rabitEnv;

  private volatile SynchronousQueue<BoosterCallable<?>> _in;
  private volatile SynchronousQueue<Object> _out;

  private Booster _booster;

  private XGBoostUpdater(
      Key modelKey, DMatrix trainMat, BoosterParms boosterParms, 
      byte[] checkpointBoosterBytes, Map<String, String> rabitEnv
  ) {
    super("XGBoostUpdater-" + modelKey);
    _modelKey = modelKey;
    _trainMat = trainMat;
    _boosterParms = boosterParms;
    _checkpointBoosterBytes = checkpointBoosterBytes;
    _rabitEnv = rabitEnv;
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
        Log.err("Updater thread was interrupted while it was still registered, name=" + self.getName());
        Log.err(e);
      } else {
        Log.debug("Updater thread interrupted.", e);
      }
      Thread.currentThread().interrupt();
    } catch (XGBoostError e) {
      Log.err("XGBoost training iteration failed");
      Log.err(e);
    } finally {
      _in = null; // Will throw NPE if used wrong
      _out = null;
      updaters.remove(_modelKey);
      try {
        _trainMat.dispose();
        if (_booster != null)
          _booster.dispose();
      } catch (Exception e) {
        Log.warn("Failed to dispose of training matrix/booster", e);
      }
      try {
        Rabit.shutdown();
      } catch (Exception xgBoostError) {
        Log.warn("Rabit shutdown during update failed", xgBoostError);
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
        Log.warn(String.format("XGBoost task of type '%s' is taking unexpectedly long, it didn't finish in %d seconds.",
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
        HashMap<String, DMatrix> watches = new HashMap<>();
        // Create initial Booster
        Booster checkpointBooster = null;
        if (_checkpointBoosterBytes != null) {
          try {
            checkpointBooster = XGBoost.loadModel(new ByteArrayInputStream(_checkpointBoosterBytes));
          } catch (IOException e) {
            throw new RuntimeException("Failed to load checkpoint booster.");
          }
        }
        _booster = ml.dmlc.xgboost4j.java.XGBoost.train(_trainMat,
                _boosterParms.get(),
                0,
                watches,
                null,
                null, 
                null, 
                0,
                checkpointBooster
              );
        // Force Booster initialization; we can call any method that does "lazy init"
        byte[] boosterBytes = _booster.toByteArray();
        Log.info("Initial Booster created, size=" + boosterBytes.length);
      } else {
        // Do one iteration
        assert _booster != null;
        _booster.update(_trainMat, _tid);
        _booster.saveRabitCheckpoint();
      }
      return _booster;
    }

    @Override
    public String toString() {
      return "Boosting Iteration (tid=" + _tid + ")";
    }
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

  Booster doUpdate(int tid) {
    try {
      return invoke(new UpdateBooster(tid));
    } catch (InterruptedException e) {
      throw new IllegalStateException("Boosting iteration failed - operation was interrupted", e);
    }
  }

  static XGBoostUpdater make(Key modelKey, DMatrix trainMat, BoosterParms boosterParms,
                             byte[] checkpoint, Map<String, String> rabitEnv) {
    XGBoostUpdater updater = new XGBoostUpdater(modelKey, trainMat, boosterParms, checkpoint, rabitEnv);
    updater.setUncaughtExceptionHandler(LoggingExceptionHandler.INSTANCE);
    if (updaters.putIfAbsent(modelKey, updater) != null)
      throw new IllegalStateException("XGBoostUpdater for modelKey=" + modelKey + " already exists!");
    return updater;
  }

  static void terminate(Key modelKey) {
    XGBoostUpdater updater = updaters.remove(modelKey);
    if (updater == null)
      Log.debug("XGBoostUpdater for modelKey=" + modelKey + " was already clean-up on node " + H2O.SELF);
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
      Log.err("Uncaught exception in " + t.getName());
      Log.err(e);
    }
  }
  
}
