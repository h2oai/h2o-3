package hex.genmodel.attributes.comparators;

import hex.genmodel.attributes.parameters.Pair;

import java.util.Comparator;

public class AscPairComparator implements Comparator<Pair<?, Double>> {
    private final boolean abs;

    public AscPairComparator(boolean abs) {
        this.abs = abs;
    }

    @Override
    public int compare(Pair<?, Double> o1, Pair<?, Double> o2) {
        if (abs)
            return Math.abs(o1.getValue()) < Math.abs(o2.getValue()) ? -1 : 0;
        return o1.getValue() < o2.getValue() ? -1 : 0;
    }
}
