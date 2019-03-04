package water.rapids;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FilterByValueTask;


public class StratificationAssistant {

  public static Frame sample(Frame fr, String sampleByColumnName, double samplingRatio, long seed) {

    final String[] STRATA_DOMAIN = new String[]{"in", "out"};

    Vec sampleByVec = fr.vec(sampleByColumnName);
    Vec stratifiedAssignmentsVec = StratifiedSplit.split(sampleByVec, samplingRatio, seed, STRATA_DOMAIN);

    double valueThatCorrespondsToInStrataLabel = 0.0;
    Frame stratifiedAssignmentsFrame = new Frame(stratifiedAssignmentsVec);
    Frame predicateFrame = new FilterByValueTask(valueThatCorrespondsToInStrataLabel, false).doAll(1, Vec.T_NUM, stratifiedAssignmentsFrame).outputFrame();

    Frame filteredFr = selectByPredicate(fr, predicateFrame);
    predicateFrame.delete();
    stratifiedAssignmentsFrame.delete();
    return filteredFr;
  }
  
  public static Frame[] split(Frame fr, String sampleByColumnName, double samplingRatio, long seed) {

    final String[] STRATA_DOMAIN = new String[]{"in", "out"};

    Vec sampleByVec = fr.vec(sampleByColumnName);
    Vec stratifiedAssignmentsVec = StratifiedSplit.split(sampleByVec, 1.0 - samplingRatio, seed, STRATA_DOMAIN);

    double valueThatCorrespondsToInStrataLabel = 0.0;
    Frame stratifiedAssignmentsFrame = new Frame(stratifiedAssignmentsVec);
    Frame predicateInFrame = new FilterByValueTask(valueThatCorrespondsToInStrataLabel, false).doAll(1, Vec.T_NUM, stratifiedAssignmentsFrame).outputFrame();

    Frame filteredIn = selectByPredicate(fr, predicateInFrame);

    Frame predicateOutFrame = new FilterByValueTask(valueThatCorrespondsToInStrataLabel, true).doAll(1, Vec.T_NUM, stratifiedAssignmentsFrame).outputFrame();
    Frame filteredOut = selectByPredicate(fr, predicateOutFrame);

    predicateInFrame.delete();
    predicateOutFrame.delete();
    stratifiedAssignmentsFrame.delete();
    return new Frame[] {filteredIn, filteredOut};
  }

  // in theory this could be done in one pass over frame, but for now I just need POC so I'm going to reuse `StratifiedSplit.split` method
  public static Frame assignKFolds(Frame fr, int numberOfFolds, String sampleByColumnName, long seed) {

    final String[] STRATA_DOMAIN = new String[]{"in", "out"};
    
    double singleFoldFraction = 1.0 / numberOfFolds;
    Frame inputFrame = fr;
    Frame filteredIn = null;
    Frame filteredOut = null;

    Frame[] splits = new Frame[numberOfFolds];
    for (int fold = 0; fold < numberOfFolds; fold++) {
      if (fold != numberOfFolds - 1 ) {
        double adjustedFraction = fold == 0 ? singleFoldFraction : singleFoldFraction * (((double) fr.numRows() / (double) inputFrame.numRows()));
        
        Vec sampleByVec = inputFrame.vec(sampleByColumnName);
        Vec stratifiedAssignmentsVec = StratifiedSplit.split(sampleByVec, 1.0 - adjustedFraction, seed, STRATA_DOMAIN);
        
        Frame stratifiedAssignmentsFrame = new Frame(stratifiedAssignmentsVec);
        double valueThatCorrespondsToInStrataLabel = 0.0;
        Frame predicateInFrame = new FilterByValueTask(valueThatCorrespondsToInStrataLabel, false).doAll(1, Vec.T_NUM, stratifiedAssignmentsFrame).outputFrame();
        filteredIn = selectByPredicate(inputFrame, predicateInFrame);

        Frame predicateOutFrame = new FilterByValueTask(valueThatCorrespondsToInStrataLabel, true).doAll(1, Vec.T_NUM, stratifiedAssignmentsFrame).outputFrame();
        filteredOut = selectByPredicate(inputFrame, predicateOutFrame);
        inputFrame = filteredOut;
      } else {
        filteredIn = inputFrame;
      }
      Frame foldFrame = new Frame(new String[]{"fold"}, new Vec[]{filteredIn.anyVec().makeCon(fold + 1)});
      splits[fold] = filteredIn.add(foldFrame);
    }
    
    Frame outputFrame = null;
    for(Frame foldFrame : splits) {
      if(outputFrame == null) {
        outputFrame = foldFrame;
      }
      else {
        outputFrame = TargetEncoderFrameHelper.rBind(outputFrame, foldFrame);
      }
    }
    
    return outputFrame;
  }

  private static Frame selectByPredicate(Frame fr, Frame predicateFrame) {
    String[] names = fr.names().clone();
    byte[] types = fr.types().clone();
    String[][] domains = fr.domains().clone();

    fr.add("predicate", predicateFrame.anyVec());
    Frame filtered = new Frame.DeepSelect().doAll(types, fr).outputFrame(Key.<Frame>make(), names, domains);
    Vec removed = fr.remove("predicate");
    removed.remove();
    return filtered;
  }
}
