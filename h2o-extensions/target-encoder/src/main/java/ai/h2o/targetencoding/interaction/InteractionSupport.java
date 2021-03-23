package ai.h2o.targetencoding.interaction;

import water.fvec.Frame;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashSet;

public class InteractionSupport {
    
    private static final String COL_INTERACTION_SEPARATOR = ":";
    
    /**
     * @return the index of the interaction column for the group, or the index of the column if the group has only one.
     */
    public static int addFeatureInteraction(Frame fr, String[] colGroup) {
        return addFeatureInteraction(fr, colGroup, null);
    }

    /**
     * @param interactionDomain the domain of the generated interaction column, if already known, 
     *                          for example when computing interaction for predictions.
     * @return the index of the interaction column for the group, 
     * or the index of the column if the group has only one, 
     * or -1 if one column in the interaction group is missing.
     */
    public static int addFeatureInteraction(Frame fr, String[] colGroup, String[] interactionDomain) {
        if (colGroup.length == 1) {
            return fr.find(colGroup[0]);
        } else if (new HashSet<>(Arrays.asList(fr.names())).containsAll(Arrays.asList(colGroup))) {
            return addInteractionColumn(fr, colGroup, interactionDomain);
        } else { // skip interaction if one col is missing  (we could also replace missing columns with a col of NAs, but we don't do this today when a simple cat column is missing)
            return -1;
        }
    }

    private static int addInteractionColumn(Frame fr, String[] interactingColumns, String[] interactionDomain) {
        String interactionColName = String.join(COL_INTERACTION_SEPARATOR, interactingColumns);  // any limit to col name length?
        int[] cols = Arrays.stream(interactingColumns).mapToInt(fr::find).toArray();
        Vec interactionCol = createInteractionColumn(fr, cols, interactionDomain);
        fr.add(interactionColName, interactionCol);
        return fr.numCols()-1;
    }

    static Vec createInteractionColumn(Frame fr, int[] interactingColumnsIdx, String[] interactionDomain) {
        String[][] interactingDomains = new String[interactingColumnsIdx.length][];
        Vec[] interactingVecs = new Vec[interactingColumnsIdx.length];
        for (int i=0; i<interactingColumnsIdx.length; i++) {
            Vec vec = fr.vec(interactingColumnsIdx[i]);
            interactingVecs[i] = vec;
            interactingDomains[i] = vec.domain();
        }
        final InteractionsEncoder encoder = new InteractionsEncoder(interactingDomains, true);
        byte interactionType = interactionDomain == null ? Vec.T_NUM : Vec.T_CAT;
        Vec interactionCol = new CreateInteractionTask(encoder, interactionDomain)
                .doAll(new byte[] {interactionType}, interactingVecs)
                .outputFrame(null, null, new String[][]{interactionDomain})
                .lastVec();
        if (interactionType != Vec.T_CAT)
            interactionCol = VecUtils.toCategoricalVec(interactionCol); // the domain is obtained from CollectDoubleDomain, so it is sorted by numerical value, and then converted to String
        return interactionCol;
    }

}
