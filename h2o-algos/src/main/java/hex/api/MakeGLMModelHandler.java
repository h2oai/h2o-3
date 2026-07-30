package hex.api;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.Model;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMOutput;
import hex.gram.Gram;
import hex.schemas.*;
import water.DKV;
import water.Iced;
import water.Key;
import water.MRTask;
import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.fvec.*;
import water.fvec.Vec.VectorGroup;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by tomasnykodym on 3/25/15.
 */
public class MakeGLMModelHandler extends Handler {

  // Deep-copies a source model's CV holdout-predictions frame into a key owned by the derived
  // model, rather than transferring the source's pointer: the source model keeps its own
  // reference (repeated derive calls stay possible, and deleting either model does not
  // invalidate the other's frame).
  private static void copyHoldoutPreds(Key<Frame> sourceHoldout, GLMModel derived, Key derivedKey) {
    if (sourceHoldout == null) return;
    Frame sourceHoldoutFrame = sourceHoldout.get();
    if (sourceHoldoutFrame == null) return;
    // derivedKey is user-controlled (args.dest), and the caller's collision check covers only that key - not this
    // second key derived from it. deepCopy + DKV.put would overwrite whatever sits here and orphan its Vecs.
    String holdoutName = derivedKey.toString() + "_cv_holdout_preds";
    if (DKV.get(Key.make(holdoutName)) != null) {
      throw new IllegalArgumentException("Cannot store the derived model's cross-validation holdout predictions: " +
              holdoutName + " already exists. Pass a different destination key, or remove that object first.");
    }
    Frame holdoutCopy = sourceHoldoutFrame.deepCopy(holdoutName);
    DKV.put(holdoutCopy);
    derived._output._cross_validation_holdout_predictions_frame_id = holdoutCopy._key;
  }

  public GLMModelV3 make_model(int version, MakeGLMModelV3 args){
    GLMModel model = DKV.getGet(args.model.key());
    if(model == null)
      throw new IllegalArgumentException("missing source model " + args.model);
    boolean multiClass = model._output._multinomial || model._output._ordinal;
    String [] names = multiClass?model._output.multiClassCoeffNames():model._output.coefficientNames(); // coefficient names in order and with Intercept
    Map<String,Double> coefs = model.coefficients();
    if (args.beta.length != names.length) {
      throw new IllegalArgumentException("model coefficient length " + names.length + " is different from coefficient" +
              " provided by user " + args.beta.length + ".\n model coefficients needed are:\n" + String.join("\n", names));
    }
    for(int i = 0; i < args.names.length; ++i)
      coefs.put(args.names[i],args.beta[i]);
    double [] beta = model.beta().clone();
    for(int i = 0; i < beta.length; ++i)
      beta[i] = coefs.get(names[i]);
    GLMModel m = new GLMModel(args.dest != null?args.dest.key():Key.make(),model._parms,null, model._ymu,
            Double.NaN, Double.NaN, -1);
    m.setInputParms(model._input_parms);
    // beta above is in the raw (denormalized) coefficient space, so the new model's DataInfo must not carry the
    // source's STANDARDIZE transform - otherwise isStandardized() reports true for a model whose beta is raw and the
    // denormalizeBeta consumers gated on it (beta(lambda), getRegularizationPath's coefficients) silently rescale
    // already-raw values. Clone first: model.dinfo() returns the source's live _output._dinfo, and the base code
    // mutated it in place, permanently flipping the *source* model to NONE.
    DataInfo dinfo = model.dinfo().clone();
    dinfo.setPredictorTransform(TransformType.NONE);
    m._output = new GLMOutput(dinfo, model._output._names, model._output._column_types, model._output._domains,
            model._output.coefficientNames(), beta, model._output._binomial, model._output._multinomial,
            model._output._ordinal, model._parms._control_variables);
    DKV.put(m._key, m);
    GLMModelV3 res = new GLMModelV3();
    res.fillFromImpl(m);
    return res;
  }

  public GLMModelV3 make_unrestricted_model(int version, MakeUnrestrictedGLMModelV3 args){
      MakeDerivedGLMModelV3 newArgs = new MakeDerivedGLMModelV3();
      newArgs.model = args.model;
      newArgs.dest = args.dest;
      newArgs.remove_offset_effects = false;
      newArgs.remove_control_variables_effects = false;
      return make_derived_model(version, newArgs);
  }

  public GLMModelV3 make_derived_model(int version, MakeDerivedGLMModelV3 args){
      GLMModel model = DKV.getGet(args.model.key());
      if (model == null)
          throw new IllegalArgumentException("Missing source model " + args.model);
      if (model._parms._control_variables == null && !model._parms._remove_offset_effects) {
          throw new IllegalArgumentException("Source model was not trained with control_variables or remove_offset_effects=True.");
      }
      Key generatedKey;
      if (args.remove_control_variables_effects && model._parms._control_variables == null) {
          throw new IllegalArgumentException("remove_control_variables_effects requires the source model " +
                  "to have been trained with control_variables.");
      }
      if (args.remove_offset_effects && !model._parms._remove_offset_effects) {
          throw new IllegalArgumentException("remove_offset_effects requires the source model " +
                  "to have been trained with remove_offset_effects=True.");
      }
      if (args.remove_control_variables_effects && args.remove_offset_effects) {
          throw new IllegalArgumentException("remove_control_variables_effects and remove_offset_effects cannot both be set: " +
                  "they produce the same model as the main model.");
      }
      if (args.remove_offset_effects) {
          generatedKey = Key.make(model._key.toString() + "_remove_offset_effects");
      } else if (args.remove_control_variables_effects) {
          generatedKey = Key.make(model._key.toString() + "_remove_control_variables_effects");
      } else {
          generatedKey = Key.make(model._key.toString()+"_unrestricted_model");
      }
      Key key = args.dest != null ? Key.make(args.dest) : generatedKey;
      Iced existingAtKey = DKV.getGet(key);
      if (existingAtKey != null) {
          if (!(existingAtKey instanceof GLMModel)) {
              throw new IllegalArgumentException("Key " + key + " already exists and does not refer to a GLM model.");
          }
          GLMModel existingModel = (GLMModel) existingAtKey;
          // Idempotent re-derive: only trust a model at this key if it was produced by a previous
          // make_derived_model call from this same source model with the same view requested. The checksum is part
          // of the identity on purpose - H2O lets you retrain into an existing model_id, so key equality alone would
          // return the *previous* fit's metrics as the derived view of the retrained model. It covers the params, the
          // training-frame content and (via GLMOutput.checksum_impl) the fitted coefficients, as they stood when the
          // model was first scored - Keyed.checksum() caches, so it is not recomputed per call.
          boolean sameProvenance = model._key.equals(existingModel._derivedFromModelId)
                  && model.checksum() == existingModel._derivedFromModelChecksum
                  && existingModel._useControlVariables == args.remove_control_variables_effects
                  && existingModel._useRemoveOffsetEffects == args.remove_offset_effects;
          if (sameProvenance) {
              GLMModelV3 existing = new GLMModelV3();
              existing.fillFromImpl(existingModel);
              return existing;
          }
          // Distinguish the two cases: _derivedFromModelId is null on any normally trained model, so the common
          // collision is a user pointing dest at a model of their own rather than at a stale derivation.
          if (existingModel._derivedFromModelId == null) {
              throw new IllegalArgumentException("Key " + key + " already holds a GLM model that was not produced by" +
                      " make_derived_glm_model. Pass a different destination key, or remove that model first.");
          }
          throw new IllegalArgumentException("Key " + key + " already holds a derived model from a different source" +
                  " model, from a different fit of the same source model, or with a different combination of" +
                  " remove_offset_effects / remove_control_variables_effects. Pass a different destination key, or" +
                  " remove that model first.");
      }
      GLMModel.GLMParameters parms = (GLMModel.GLMParameters) model._parms.clone();
      GLMModel.GLMParameters inputParms = (GLMModel.GLMParameters) model._input_parms.clone();
      GLMModel m = new GLMModel(key, parms,null, model._ymu,
              Double.NaN, Double.NaN, -1);
      m.setInputParms(inputParms);
      if (args.remove_control_variables_effects){
          m._input_parms._control_variables = model._parms._control_variables;
          m._parms._control_variables = model._parms._control_variables;
          m._input_parms._remove_offset_effects = false;
          m._parms._remove_offset_effects = false;
      } else if(args.remove_offset_effects){
          m._input_parms._remove_offset_effects = true;
          m._parms._remove_offset_effects = true;
          m._input_parms._control_variables = null;
          m._parms._control_variables = null;
      } else {
          m._input_parms._control_variables = null;
          m._parms._control_variables = null;
          m._input_parms._remove_offset_effects = false;
          m._parms._remove_offset_effects = false;
      }
      // beta() is in the raw (denormalized) coefficient space, so the derived model's DataInfo must
      // not carry the source's STANDARDIZE transform, or coef_norm() will re-standardize raw values.
      DataInfo dinfo = model.dinfo().clone();
      dinfo.setPredictorTransform(TransformType.NONE);
      m._output = new GLMOutput(dinfo, model._output._names, model._output._column_types, model._output._domains,
              model._output.coefficientNames(), model._output.beta(), model._output._binomial, model._output._multinomial,
              model._output._ordinal, null);
      // The GLMOutput ctor above synthesizes a placeholder submodel at lambda=0/alpha=0. Carry the source's
      // selected lambda/alpha over, otherwise the derived model reports lambda_best()/alpha_best() == 0 - and
      // learning_curve_plot(), which filters the copied scoring history by alpha_best, silently plots nothing.
      m._output.retagDerivedSubmodel(model._output.lambda_best(), model._output.alpha_best());
      // _training_metrics/_validation_metrics/_cross_validation_metrics below are shared by reference
      // with the source model, not deep-copied: they're inline (non-Key) ModelMetrics fields, so the
      // DKV.put(key, m) at the end of this method already serializes them by value into the derived
      // model's own DKV entry. The derived model never re-resolves them through DKV afterward (no code
      // in the codebase does an independent by-key ModelMetrics lookup for a derived model), so sharing
      // the reference is safe regardless of what later happens to the source model.
      if (args.remove_control_variables_effects) {
          // _contr_vals slots are only populated when both control_variables + remove_offset_effects are combined;
          // with control_variables alone the main slots already hold the restricted view.
          boolean hasBothFeatures = model._parms._remove_offset_effects;
          m._output._training_metrics = hasBothFeatures
                  ? model._output._training_metrics_restricted_model_contr_vals
                  : model._output._training_metrics;
          m._output._validation_metrics = hasBothFeatures
                  ? model._output._validation_metrics_restricted_model_contr_vals
                  : model._output._validation_metrics;
          m._output._scoring_history = hasBothFeatures
                  ? model._output._scoring_history_restricted_model_contr_vals
                  : model._output._scoring_history;
          m.resetThreshold(model.defaultThreshold());
          m._output._variable_importances = model._output._variable_importances;
          m._output.setAndMapControlVariablesNames(model._parms._control_variables);
      } else if (args.remove_offset_effects) {
          // _restricted_model_ro slots are only populated when control_variables + remove_offset_effects
          // are combined; with remove_offset_effects alone the main slots already hold the restricted view.
          boolean hasBothFeatures = model._parms._control_variables != null;
          m._output._training_metrics = hasBothFeatures
                  ? model._output._training_metrics_restricted_model_ro
                  : model._output._training_metrics;
          m._output._validation_metrics = hasBothFeatures
                  ? model._output._validation_metrics_restricted_model_ro
                  : model._output._validation_metrics;
          m._output._scoring_history = hasBothFeatures
                  ? model._output._scoring_history_restricted_model_ro
                  : model._output._scoring_history;
          m._output._cross_validation_metrics = model._output._cross_validation_metrics;
          m._output._cross_validation_metrics_summary = model._output._cross_validation_metrics_summary;
          m.resetThreshold(model.defaultThreshold());
          m._output._variable_importances = model._output._variable_importances_unrestricted_model;
          copyHoldoutPreds(model._output._cross_validation_holdout_predictions_frame_id, m, key);
      } else {
          m._output._training_metrics = model._output._training_metrics_unrestricted_model;
          m._output._validation_metrics = model._output._validation_metrics_unrestricted_model;
          m._output._scoring_history = model._output._scoring_history_unrestricted_model;
          m.resetThreshold(model.defaultThreshold());
          m._output._variable_importances = model._output._variable_importances_unrestricted_model;
          // Unrestricted (with-offset) CV view from source's parity slots; null when
          // _remove_offset_effects=false or nfolds=0.
          m._output._cross_validation_metrics = model._output._cross_validation_metrics_unrestricted_model;
          m._output._cross_validation_metrics_summary = model._output._cross_validation_metrics_summary_unrestricted_model;
          copyHoldoutPreds(model._output._cross_validation_holdout_predictions_frame_id_unrestricted_model, m, key);
      }
      m._output._model_summary = model._output._model_summary;
      m._key = key;
      // setting these flags is important for right scoring
      m._useControlVariables = args.remove_control_variables_effects;
      m._useRemoveOffsetEffects = args.remove_offset_effects;
      m._derivedFromModelId = model._key;
      m._derivedFromModelChecksum = model.checksum();

      DKV.put(key, m);

      GLMModelV3 res = new GLMModelV3();
      res.fillFromImpl(m);
      return res;
  }

  public GLMRegularizationPathV3 extractRegularizationPath(int v, GLMRegularizationPathV3 args) {
    GLMModel model = DKV.getGet(args.model.key());
    if(model == null)
      throw new IllegalArgumentException("missing source model " + args.model);
    return new GLMRegularizationPathV3().fillFromImpl(model.getRegularizationPath());
  }
  // instead of adding a new endpoint, just put this stupid test functionality here
 /** Get the expanded (interactions + offsets) dataset. Careful printing! Test only
  */
  public DataInfoFrameV3 getDataInfoFrame(int version, DataInfoFrameV3 args) {
    Frame fr = DKV.getGet(args.frame.key());
    if( null==fr ) throw new IllegalArgumentException("no frame found");
    args.result = new KeyV3.FrameKeyV3(oneHot(fr, Model.InteractionSpec.allPairwise(args.interactions), args.use_all, args.standardize, args.interactions_only, true)._key);
    return args;
  }

  public static Frame oneHot(Frame fr, Model.InteractionSpec interactions, boolean useAll, boolean standardize, final boolean interactionsOnly, final boolean skipMissing) {
    final DataInfo dinfo = new DataInfo(fr,null,1,useAll,standardize?TransformType.STANDARDIZE:TransformType.NONE,TransformType.NONE,skipMissing,false,false,false,false,false, interactions);
    Frame res;
    if( interactionsOnly ) {
      if( null==dinfo._interactionVecs ) throw new IllegalArgumentException("no interactions");
      int noutputs=0;
      final int[] colIds = new int[dinfo._interactionVecs.length];
      final int[] offsetIds = new int[dinfo._interactionVecs.length];
      int idx=0;
      String[] coefNames = dinfo.coefNames();
      for(int i : dinfo._interactionVecs)
        noutputs+= ( offsetIds[idx++] = ((InteractionWrappedVec)dinfo._adaptedFrame.vec(i)).expandedLength());
      String[] names = new String[noutputs];
      int offset=idx=0;
      int namesIdx=0;
      for(int i=0;i<dinfo._adaptedFrame.numCols();++i) {
        Vec v = dinfo._adaptedFrame.vec(i);
        if( v instanceof InteractionWrappedVec ) { // ding! start copying coefNames into names while offset < colIds[idx+1]
          colIds[idx] = offset;
          for(int nid=0;nid<offsetIds[idx];++nid)
            names[namesIdx++] = coefNames[offset++];
          idx++;
          if( idx > dinfo._interactionVecs.length ) break; // no more interaciton vecs left
        } else {
          if( v.isCategorical() ) offset+= v.domain().length - (useAll?0:1);
          else                    offset++;
        }
      }
      res = new MRTask() {
        @Override public void map(Chunk[] cs, NewChunk ncs[]) {
          DataInfo.Row r = dinfo.newDenseRow();
          for(int i=0;i<cs[0]._len;++i) {
            r=dinfo.extractDenseRow(cs,i,r);
            if( skipMissing && r.isBad() ) continue;
            int newChkIdx=0;
            for(int idx=0;idx<colIds.length;++idx) {
              int startOffset = colIds[idx];
              for(int start=startOffset;start<(startOffset+offsetIds[idx]);++start )
                ncs[newChkIdx++].addNum(r.get(start));
            }
          }
        }
      }.doAll(noutputs,Vec.T_NUM,dinfo._adaptedFrame).outputFrame(Key.make(),names,null);
    } else {
      byte[] types = new byte[dinfo.fullN()];
      Arrays.fill(types, Vec.T_NUM);
      res = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk ncs[]) {
          DataInfo.Row r = dinfo.newDenseRow();
          for (int i = 0; i < cs[0]._len; ++i) {
            r = dinfo.extractDenseRow(cs, i, r);
            if( skipMissing && r.isBad() ) continue;
            for (int n = 0; n < ncs.length; ++n)
              ncs[n].addNum(r.get(n));
          }
        }
      }.doAll(types, dinfo._adaptedFrame.vecs()).outputFrame(Key.make("OneHot"+Key.make().toString()), dinfo.coefNames(), null);
    }
    dinfo.dropInteractions();
    dinfo.remove();
    return res;
  }
  public GramV3 computeGram(int v, GramV3 input){
    if(DKV.get(input.X.key()) == null)
      throw new IllegalArgumentException("Frame " + input.X.key() + " does not exist.");
    Frame fr = input.X.key().get();
    Frame frcpy = new Frame(fr._names.clone(),fr.vecs().clone());
    String wname = null;
    Vec weight = null;
    if(input.W != null && !input.W.column_name.isEmpty()) {
      wname = input.W.column_name;
      if(fr.find(wname) == -1) throw new IllegalArgumentException("Did not find weight vector " + wname);
      weight = frcpy.remove(wname);
    }
    DataInfo dinfo = new DataInfo(frcpy,null,0,input.use_all_factor_levels,input.standardize?TransformType.STANDARDIZE:TransformType.NONE,TransformType.NONE,input.skip_missing,false,!input.skip_missing,/* weight */ false, /* offset */ false, /* fold */ false, /* intercept */ true);
    DKV.put(dinfo);
    if(weight != null)dinfo.setWeights(wname,weight);
    Gram.GramTask gt = new Gram.GramTask(null,dinfo,false,true).doAll(dinfo._adaptedFrame);
    double [][] gram = gt._gram.getXX();
    dinfo.remove();
    String [] names = water.util.ArrayUtils.append(dinfo.coefNames(),"Intercept");
    Vec [] vecs = new Vec[gram.length];
    Key[] keys = new VectorGroup().addVecs(vecs.length);
    for(int i = 0; i < vecs.length; ++i)
      vecs[i] = Vec.makeVec(gram[i],keys[i]);
    input.destination_frame = new KeyV3.FrameKeyV3();
    String keyname = input.X.key().toString();
    if(keyname.endsWith(".hex"))
      keyname = keyname.substring(0,keyname.lastIndexOf("."));
    keyname = keyname + "_gram";
    if(weight != null)
      keyname = keyname + "_" + wname;
    Key k = Key.make(keyname);
    if(DKV.get(k) != null){
      int cnt = 0;
      while(cnt < 1000 && DKV.get(k = Key.make(keyname + "_" + cnt)) != null)cnt++;
      if(cnt == 1000) throw new IllegalArgumentException("unable to make unique key");
    }
    input.destination_frame.fillFromImpl(k);
    DKV.put(new Frame(k, names,vecs));
    return input;
  }
}
