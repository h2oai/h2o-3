package water.api;

import hex.*;
import water.*;
import water.fvec.Frame;
import water.fvec.RollupStats;
import water.fvec.Vec;

class FramesHandler extends Handler<FramesHandler.Frames, FramesBase> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the frames list and params. */
  protected static final class Frames extends Iced {
    Key key;
    Frame[] frames;
    String column;

    /**
     * Fetch all Frames from the KV store.
     */
    protected static Frame[] fetchAll() {
      // Get all the frames.
      final Key[] frameKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return k._type == TypeMap.FRAME;
        }
      }).keys();
      Frame[] frames = new Frame[frameKeys.length];
      for (int i = 0; i < frameKeys.length; i++) {
        Frame frame = getFromDKV(frameKeys[i]);
        frames[i] = frame;
      }
      return frames;
    }
  }


  /**
   * Score a frame with the given model.
   */
  protected static ModelMetrics scoreOne(Frame frame, Model score_model) {

    ModelMetrics metrics = ModelMetrics.getFromDKV(score_model, frame);

    if (null == metrics) {
      // have to compute
      water.util.Log.debug("Cache miss: computing ModelMetrics. . .");
      long before = System.currentTimeMillis();
      Frame predictions = score_model.score(frame, true); // TODO: for now we're always calling adapt inside score
      long after = System.currentTimeMillis();

      ConfusionMatrix cm = new ConfusionMatrix(); // for regression this computes the MSE
      AUC auc = null;
      HitRatio hr = null;

      if (score_model._output.getModelCategory() == Model.ModelCategory.Binomial || score_model._output.getModelCategory() == Model.ModelCategory.Multinomial) {
        SupervisedModel sm = (SupervisedModel)score_model;
        auc = new AUC();
//      hr = new HitRatio();
        sm.calcError(frame, frame.vec(score_model._output.responseName()), predictions, predictions, "Prediction error:",
                true, 20, cm, auc, hr);
      } else if (score_model._output.getModelCategory() == Model.ModelCategory.Regression) {
        SupervisedModel sm = (SupervisedModel)score_model;
        sm.calcError(frame, frame.vec(score_model._output.responseName()), predictions, predictions, "Prediction error:",
                true, 20, cm, null, null);
      } else {
        // TODO: currently we don't do error metrics for clustering.  Need to return an MSE/etc ModelMetrics.
      }

      // Now call AUC and ConfusionMatrix and maybe HitRatio
      metrics = new ModelMetrics(score_model,
              score_model._output.getModelCategory(),
              frame,
              after - before,
              after,
              (auc == null ? null : auc.data()),
              cm);

      // Put the metrics into the KV store
      metrics.putInDKV();
    } else {
      // it's already cached in the DKV
      water.util.Log.debug("using ModelMetrics from the cache. . .");
    }

    return metrics;
  }



  // /2/Frames backward compatibility: uses ?key parameter and returns either a single frame or all.
  public Schema list_or_fetch(int version, Frames f) {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);

    if (null != f.key) {
      return fetch(version, f);
    } else {
      return list(version, f);
    }
  }

  /** Return all the frames. */
  public Schema list(int version, Frames f) {
    f.frames = Frames.fetchAll();
    return this.schema(version).fillFromImpl(f);
  }

  /** NOTE: We really want to return a different schema here! */
  public Schema columns(int version, Frames f) {
    // TODO: return *only* the columns. . .  This may be a different schema.
    return fetch(version, f);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Frame))
      throw new IllegalArgumentException("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());

    return (Frame)ice;
  }

  /** Return a single column from the frame. */
  public Schema column(int version, Frames f) { // TODO: should return a Vec schema
    Frame frame = getFromDKV(f.key);

    // TODO: We really want to return a different schema here!
    Vec vec = frame.vec(f.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + f.column + " in frame: " + f.key.toString());

    Vec[] vecs = { vec };
    String[] names = { f.column };
    Frame new_frame = new Frame(names, vecs);
    f.frames = new Frame[1];
    f.frames[0] = new_frame;
    return this.schema(version).fillFromImpl(f);
  }

  public FramesBase columnSummary(int version, Frames frames) {
    Frame frame = getFromDKV(frames.key);
    Vec vec = frame.vec(frames.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + frames.column + " in frame: " + frames.key.toString());

    // Compute second pass of rollups: the histograms.  Side-effects the Vec.
    // TODO: side effects, ugh.
    RollupStats.computeHisto(vec);

    // Cons up our result
    frames.frames = new Frame[1];
    frames.frames[0] = new Frame(new String[] {frames.column }, new Vec[] { vec });
    return schema(version).fillFromImpl(frames);
  }

  /** Return a single frame. */
  public Schema fetch(int version, Frames f) {
    Frame frame = getFromDKV(f.key);
    f.frames = new Frame[1];
    f.frames[0] = frame;
    return this.schema(version).fillFromImpl(f);
  }

  // Remove an unlocked frame.  Fails if frame is in-use
  public void delete(int version, Frames frames) {
    Frame frame = getFromDKV(frames.key);
    frame.delete();             // lock & remove
  }

  // Remove ALL an unlocked frames.  Throws IAE for all deletes that failed
  // (perhaps because the Frames were locked & in-use).
  public void deleteAll(int version, Frames frames) {
    final Key[] frameKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          return k._type == TypeMap.FRAME;
        }
      }).keys();

    String err=null;
    Futures fs = new Futures();
    for( int i = 0; i < frameKeys.length; i++ ) {
      try {
        getFromDKV(frameKeys[i]).delete(null,fs);
      } catch( IllegalArgumentException iae ) {
        err += iae.getMessage();
      }
    }
    fs.blockForPending();
    if( err != null ) throw new IllegalArgumentException(err);
  }


  @Override protected FramesBase schema(int version) {
    switch (version) {
    case 2:   return new FramesV2();
    case 3:   return new FramesV3();
    default:  throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
