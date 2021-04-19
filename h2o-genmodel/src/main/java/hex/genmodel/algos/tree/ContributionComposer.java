package hex.genmodel.algos.tree;

import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.utils.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

public class ContributionComposer implements Serializable {
    
    /**
     * Sort shapley values and compose desired output
     *
     * @param contribs Raw contributions to be composed
     * @param contribNames Contribution corresponding feature names
     * @param topN Return only #topN highest contributions + bias.
     * @param topBottomN Return only #topBottomN lowest contributions + bias
     *                   If topN and topBottomN are defined together then return array of #topN + #topBottomN + bias
     * @param abs True to compare absolute values of contributions
     * @return Sorted KeyValue array of contributions of size #topN + #topBottomN + bias
     *         If topN < 0 || topBottomN < 0 then all descending sorted contributions is returned.
     */
    public final KeyValue[] composeContributions(float[] contribs, String[] contribNames, int topN, int topBottomN, boolean abs) {
        if (topBottomN == 0) {
            return composeSortedContributions(contribs, contribNames, topN, new KeyValue.DescComparator(abs));
        } else if (topN == 0) {
            return composeSortedContributions(contribs, contribNames, topBottomN, new KeyValue.AscComparator(abs));
        } else if ((topN + topBottomN) >= contribs.length || topN < 0 || topBottomN < 0) {
            return composeSortedContributions(contribs, contribNames, contribs.length, new KeyValue.DescComparator(abs));
        }

        KeyValue[] topSorted = composeSortedContributions(contribs, contribNames, contribs.length, new KeyValue.DescComparator(abs));
        KeyValue[] bottomSorted = Arrays.copyOfRange(topSorted, topSorted.length - 1 - topBottomN, topSorted.length);
        reverse(bottomSorted, bottomSorted.length - 1);
        topSorted = Arrays.copyOf(topSorted, topN);

        return ArrayUtils.appendGeneric(topSorted, bottomSorted);
    }
    
    public int checkAndAdjustInput(int n, int len) {
        if (n < 0 || n > len) {
            return len;
        }
        return n;
    }
    
    private KeyValue[] composeSortedContributions(float[] contribs, String[] contribNames, int n, Comparator<? super KeyValue> comparator) {
        int nAdjusted = checkAndAdjustInput(n, contribs.length);
        KeyValue[] sortedContributions = sortContributions(contribs, contribNames, comparator);
        if (nAdjusted < contribs.length) {
            KeyValue bias = sortedContributions[contribs.length-1];
            sortedContributions = Arrays.copyOfRange(sortedContributions, 0, nAdjusted + 1);
            sortedContributions[nAdjusted] = bias;
        }
        return sortedContributions;
    }
    
    private KeyValue[] sortContributions(float[] contribs, String[] contribNames, Comparator<? super KeyValue> comparator) {
        KeyValue[] sorted = new KeyValue[contribs.length];
        for (int i = 0; i < contribs.length; i++) {
            sorted[i] = new KeyValue(contribNames[i], contribs[i]);
        }
        Arrays.sort(sorted, 0, contribs.length -1 /*exclude bias*/, comparator);
        return sorted;
    }

    private void reverse(KeyValue[] contributions, int len) {
        for (int i = 0; i < len/2; i++) {
            KeyValue tmp = contributions[i];
            contributions[i] = contributions[len - i - 1];
            contributions[len - i - 1] = tmp;
        }
    }
}
