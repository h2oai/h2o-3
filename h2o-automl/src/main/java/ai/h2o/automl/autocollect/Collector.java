package ai.h2o.automl.autocollect;


import ai.h2o.automl.AutoML;
import ai.h2o.automl.collectors.AutoLinuxProcFileReader;
import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.IcedWrapper;
import water.Key;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class Collector {
  protected static Thread resourceCollector; // special collector for RSS & CPU
  protected static final double[] SPLITRATIOS = new double[]{0.8,0.2};
  static {
    resourceCollector=new Thread(new Runnable() {
      @Override
      public void run() {
        //Collect resources info...

        //HashMap to store resource info
        HashMap<String, Object> getProc = new HashMap<>();

        //Put elements into hash map
        getProc.put("RSS", AutoLinuxProcFileReader.getSystemTotalTicks());
        getProc.put("SysCPU", AutoLinuxProcFileReader.getSystemTotalTicks());
        getProc.put("ProcCPU", AutoLinuxProcFileReader.getProcessTotalTicks());
        getProc.put("timestamp",AutoLinuxProcFileReader.getTimeStamp());

        //Push to ResourceMeta
        AutoCollect.pushResourceMeta(getProc);
      }
    });
  }

  public void collect0(int idFrame, Frame fr, long seedSplit, HashSet<String> configs) {
    Frame[] fs;
    Key[] trainTestKeys = new Key[]{Key.make(),Key.make()};
    fs = ShuffleSplitFrame.shuffleSplitFrame(fr, trainTestKeys, SPLITRATIOS, seedSplit);  // split data
    try {
      collect(fs[0], fs[1], idFrame, seedSplit, configs);
    } catch( Exception ex ) {
      ex.printStackTrace();
    } finally {
      for( Frame f: fs) f.delete();
    }
  }

  protected abstract void collect(Frame train, Frame valid, int idFrame, long seed, HashSet<String> configs);
  protected abstract Model.Parameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs);


    protected void logScoreHistory(ModelBuilder mb, Model m, String configID) {
    HashMap<String,Object> scoreHistory = newScoreHist();
    List<String> colHeaders = Arrays.asList(m._output._scoring_history.getColHeaders());
    for( IcedWrapper[] iw: m._output._scoring_history.getCellValues() ) {
      int idx;
      if( (idx=colHeaders.indexOf("Timestamp"))!=-1 )                       scoreHistory.put("ts",                                  iw[idx].get());
      if( (idx=colHeaders.indexOf("Training MSE"))!=-1 )                    scoreHistory.put("train_mse",                  sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training LogLoss"))!=-1 )                scoreHistory.put("train_logloss",              sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training Classification Error"))!=-1 )   scoreHistory.put("train_classification_error", sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation MSE"))!=-1 )                  scoreHistory.put("test_mse",                   sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("validation LogLoss"))!=-1 )              scoreHistory.put("test_logloss",               sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation Classification Error"))!=-1 ) scoreHistory.put("test_classification_error",  sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training Deviance"))!=-1 )               scoreHistory.put("train_deviance",             sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation Deviance"))!=-1 )             scoreHistory.put("test_deviance",              sanitize(iw[idx].get()));
      AutoCollect.pushMeta(scoreHistory, scoreHistory.keySet().toArray(new String[scoreHistory.size()]), "ScoreHistory", null);
    }
  }
  private static double sanitize(Object d) { return Double.isNaN((double)d) ? AutoML.SQLNAN : (double)d; }
  protected HashMap<String,Object> newScoreHist() {
    HashMap<String, Object> hm = new HashMap<>();
    hm.put("ts",AutoML.SQLNAN);
    hm.put("train_mse", AutoML.SQLNAN);
    hm.put("train_logloss",AutoML.SQLNAN);
    hm.put("train_classification_error", AutoML.SQLNAN);
    hm.put("test_mse", AutoML.SQLNAN);
    hm.put("test_logloss",AutoML.SQLNAN);
    hm.put("test_classification_error", AutoML.SQLNAN);
    hm.put("train_deviance", AutoML.SQLNAN);
    hm.put("test_deviance", AutoML.SQLNAN);
    return hm;
  }

  protected boolean isValidConfig(String configID, HashSet<String> configs) { return !configs.contains(configID); }
}
