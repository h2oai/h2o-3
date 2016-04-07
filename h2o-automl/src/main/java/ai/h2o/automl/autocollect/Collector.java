package ai.h2o.automl.autocollect;


import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.H2O;
import water.IcedWrapper;
import water.Key;
import water.fvec.Frame;
import water.util.LinuxProcFileReader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class Collector {
  private Thread resourceCollector; // special collector for RSS & CPU
  protected static final double[] SPLITRATIOS = new double[]{0.8,0.2};

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
      collectJVMSettings(configID);
      startResourceCollection(configID);
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

  protected static void collectJVMSettings(String configID) {
    HashMap<String, Object> hm = new HashMap<>();
    hm.put("ConfigID", configID);
    hm.put("build_branch", H2O.ABV.branchName());
    hm.put("build_date", H2O.ABV.compiledOn());
    hm.put("build_sha",  H2O.ABV.lastCommitHash());
    hm.put("java_version", System.getProperty("java.version"));
    hm.put("Xmx_GB", (double)Runtime.getRuntime().maxMemory() / (1<<30) );
    AutoCollect.pushMeta(hm, hm.keySet().toArray(new String[hm.size()]), "JVMSettings", null);
  }

  protected void startResourceCollection(final String ConfigID) {
    resourceCollector = new Thread(new Runnable() {
      private long[][] _ticks;
      @Override public void run() {
        HashMap<String, Object> hm = new HashMap<>();
        LinuxProcFileReader lpfr = new LinuxProcFileReader();
        while(true) {
          lpfr.read();
          if( lpfr.valid() ) {
            hm.put("ConfigID", ConfigID);
            hm.put("ts", System.currentTimeMillis());
            hm.put("rss", AutoCollect.SQLNAN);
            hm.put("sys_cpu", AutoCollect.SQLNAN);
            hm.put("proc_cpu", AutoCollect.SQLNAN);
            hm.put("num_cpu", AutoCollect.SQLNAN);
            hm.put("rss", lpfr.getProcessRss());
            long[][] newTicks = new long[lpfr.getCpuTicks().length][];
            for(int i=0;i<newTicks.length;++i)
              newTicks[i] = lpfr.getCpuTicks()[i].clone();
            if( _ticks == null ) _ticks = newTicks;
            else {
              double deltaUser = deltaAv(_ticks, newTicks, 0);
              double deltaSys = deltaAv(_ticks, newTicks, 1);
              double deltaOther = deltaAv(_ticks, newTicks, 2);
              double deltaIdle = deltaAv(_ticks, newTicks, 3);
              double deltaTotal = deltaUser + deltaSys + deltaOther + deltaIdle;
              if (deltaTotal > 0) {
                hm.put("sys_cpu", deltaSys / deltaTotal);
                hm.put("proc_cpu", deltaUser / deltaTotal);
              }
              hm.put("num_cpu", _ticks.length);
              _ticks = newTicks;
            }
            AutoCollect.pushResourceMeta(hm);
          }
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ex) {
            // ignore
          }
        }
      }
    });
    resourceCollector.start();
  }

  private double deltaAv(long[][] Old, long[][] New, int i) { // averaged over num CPUs
    int div=Old.length; // number of CPUs
    long val=0;
    for(int j=0;j<div;++j)
      val += New[j][i] - Old[j][i];
    return (double)val/(double)div;
  }

  protected void stopResourceCollection() {
    if( !resourceCollector.isInterrupted() ) resourceCollector.interrupt();
  }
}
