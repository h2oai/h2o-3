package water.rapids;

import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FilterByValueTask;

public class StratifiedSampler {

  
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
