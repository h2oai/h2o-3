package hex.deeplearning;

import hex.*;
import water.Job;
import water.Key;
import water.fvec.Frame;

/**
 * Common interface for DeepLearning something
 * Created by vpatryshev on 12/29/16.
 */
public interface DeepLearning<M extends SimpleDLM> {
  ModelCategory[] can_build();
  int nclasses();
  boolean isClassifier();
  void error(String field_name, String message);
  void warn (String field_name, String message);
  void hide (String field_name, String message);
  void checkDistributions();
  boolean hasOffsetCol();
  DeepLearningParameters params();

  // TODO(vlad): kick these out
  public Frame train();
  public Frame valid();
  public Job<M> job();
  Key<M> dest();
  // Kick the methods above out

  DataInfo makeDataInfo(Frame train, Frame valid, DeepLearningParameters parms, int nClasses);
    
  boolean havePojo();

  boolean haveMojo();

  ToEigenVec getToEigenVec();

  boolean isSupervised();

  void checkMyConditions();

  <D extends Driver> D trainModelImpl();

//  void init0();
//  void init1();
  void init(boolean expensive);

  void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders);

  long computeTrainSamplesPerIteration(DeepLearningParameters mp, long training_rows, M model);
}
