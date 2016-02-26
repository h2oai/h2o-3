package ai.h2o.automl.autocollect;


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
  private Thread resourceCollector; // special collector for RSS & CPU
  protected static final double[] SPLITRATIOS = new double[]{0.8,0.2};

  protected void startResourceCollection() {
    resourceCollector = new Thread(new Runnable() {
      @Override public void run() {
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          // ignore
        }
      }
    });
    resourceCollector.start();
  }

  protected void stopResourceCollection() {
    if( !resourceCollector.isInterrupted() ) resourceCollector.interrupt();
  }

  public void collect(int idFrame, Frame fr, Frame test, String[] ignored, String y, long seedSplit, HashSet<String> configs) {
    System.out.println("Collecting: " + getClass());
    Frame[] fs=null;
    if( test==null )
      fs = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(),Key.make()}, SPLITRATIOS.clone(), seedSplit);  // split data
    try {
      ModelBuilder mb = makeModelBuilder(genParms(seedSplit,idFrame,fr.numCols(),configs));
      mb._parms._train=fs==null?fr._key:fs[0]._key;
      mb._parms._valid=fs==null?test._key:fs[1]._key;
      mb._parms._ignored_columns = ignored;
      mb._parms._response_column = y;
      collect0(mb, configId(mb._parms, idFrame));
    } finally {
      if( fs!=null )
        for( Frame f: fs) f.delete();
    }
  }

  // the default collector, custom collect call in GLMCollect
  private void collect0(ModelBuilder mb, String configID) {
    Model m = null;
    try {
      startResourceCollection();
      m = (Model)mb.trainModel().get();
      stopResourceCollection();
      logScoreHistory(mb,m,configID);
    } catch( Exception ex ) {
      ex.printStackTrace();
    } finally {
      if( !resourceCollector.isInterrupted() ) resourceCollector.interrupt();
      if( m!=null ) m.delete();
    }
  }
  protected abstract Model.Parameters genParms(long seedSplit, int idFrame, int ncol, HashSet<String> configs);
  protected abstract ModelBuilder makeModelBuilder(Model.Parameters p);
  protected abstract String configId(Model.Parameters p, int idFrame);

  protected void logScoreHistory(ModelBuilder mb, Model m, String configID) {
    HashMap<String,Object> scoreHistory = newScoreHist();
    List<String> colHeaders = Arrays.asList(m._output._scoring_history.getColHeaders());
    for( IcedWrapper[] iw: m._output._scoring_history.getCellValues() ) {
      scoreHistory.put("ConfigID", configID);
      int idx;
      if( (idx=colHeaders.indexOf("Timestamp"))!=-1 )                       scoreHistory.put("ts",                                  iw[idx].get());
      if( (idx=colHeaders.indexOf("Training MSE"))!=-1 )                    scoreHistory.put("train_mse",                  sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training LogLoss"))!=-1 )                scoreHistory.put("train_logloss",              sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training Classification Error"))!=-1 )   scoreHistory.put("train_classification_error", sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation MSE"))!=-1 )                  scoreHistory.put("test_mse",                   sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation LogLoss"))!=-1 )              scoreHistory.put("test_logloss",               sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation Classification Error"))!=-1 ) scoreHistory.put("test_classification_error",  sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Training Deviance"))!=-1 )               scoreHistory.put("train_deviance",             sanitize(iw[idx].get()));
      if( (idx=colHeaders.indexOf("Validation Deviance"))!=-1 )             scoreHistory.put("test_deviance",              sanitize(iw[idx].get()));
      AutoCollect.pushMeta(scoreHistory, scoreHistory.keySet().toArray(new String[scoreHistory.size()]), "ScoreHistory", null);
    }
  }
  private static double sanitize(Object d) { return Double.isNaN((double)d) ? AutoCollect.SQLNAN : (double)d; }
  protected HashMap<String,Object> newScoreHist() {
    HashMap<String, Object> sh = new HashMap<>();
    sh.put("ConfigID","");
    sh.put("ts", AutoCollect.SQLNAN);
    sh.put("train_mse", AutoCollect.SQLNAN);
    sh.put("train_logloss", AutoCollect.SQLNAN);
    sh.put("train_classification_error", AutoCollect.SQLNAN);
    sh.put("test_mse", AutoCollect.SQLNAN);
    sh.put("test_logloss", AutoCollect.SQLNAN);
    sh.put("test_classification_error", AutoCollect.SQLNAN);
    sh.put("train_deviance", AutoCollect.SQLNAN);
    sh.put("test_deviance", AutoCollect.SQLNAN);
    return sh;
  }

  protected boolean isValidConfig(String configID, HashSet<String> configs) { return !configs.contains(configID); }
}
