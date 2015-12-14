package ai.h2o.automl;

import water.fvec.Frame;

/**
 * Initial draft of AutoML
 *
 * AutoML is a node-local driver class that is responsible for managing multiple threads
 * of execution in an effort to discover an optimal supervised model for some given
 * (dataset, response, loss) combo.
 */
public final class AutoML {
  private final Frame _fr;               // all learning on this frame
  private final int _response;           // response column, -1 for no response column
  private final String _loss;            // overarching loss to minimize (not within algo loss, but without)
  private final long _maxTime;           // maximum amount of time allotted to automl
  private final double _minAcc;          // minimum accuracy to achieve
  private final boolean _ensemble;       // allow ensembles?
  private final models[] _modelEx;       // model types to exclude; e.g. don't allow DL whatsoever
  private final boolean _allowMutations; // allow for INPLACE mutations on input frame

  enum models { GBM, RF, GLM, GLRM, DL }

  // https://0xdata.atlassian.net/browse/STEAM-52  --more interesting user options
  public AutoML(Frame fr, int response, String loss, long maxTime, double minAccuracy, boolean ensemble, String[] modelExclude, boolean allowMutations) {
    _fr=fr;
    _response=response;
    _loss=loss;
    _maxTime=maxTime;
    _minAcc=minAccuracy;
    _ensemble=ensemble;
    _modelEx=modelExclude==null?null:new models[modelExclude.length];
    if( modelExclude!=null )
      for( int i=0; i<modelExclude.length; ++i )
        _modelEx[i] = models.valueOf(modelExclude[i]);
    _allowMutations=allowMutations;
  }

  // manager thread:
  //  1. Do extremely cursory pass over data and gather only the most basic information.
  //
  //     During this pass, AutoML will learn how timely it will be to do more info
  //     gathering on _fr. There's already a number of interesting stats available
  //     thru the rollups, so no need to do too much too soon.
  //
  //  2. Build a very dumb RF (with stopping_rounds=1, stopping_tolerance=0.01)
  //
  //  3. TODO: refinement passes and strategy selection
  //
  public void learn() {
    // guess if classification or regression problem
    // vec type, name, data, and the loss method help to inform the guess

  }
}
