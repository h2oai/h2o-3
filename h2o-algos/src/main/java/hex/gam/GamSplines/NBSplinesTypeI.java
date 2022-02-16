package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.List;

import static hex.gam.GamSplines.NBSplinesUtils.fillKnots;

public class NBSplinesTypeI {
    private final int _order;
    private final int _numKnots;
    private int _index;   // start index of its knots off the original knot sequence with duplication
    private List<Double> _nodeCoeffs;
    private double[] _coeffChild;   // coefficient to pass down to child
    private List<Double> _knots;
    private double _commonConst;
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;

    /**
     * 
     * @param knots : containing knots that is from spline of _order+1, or the whole list of knots
     * @param order : parent spline function order
     * @param basisIndex : basis function index
     */
    public NBSplinesTypeI(List<Double> knots, int order, int basisIndex) {
        _order= order;
        _numKnots = knots.size();
        _knots = new ArrayList<>(knots.subList(basisIndex, basisIndex+order));
        _commonConst = (knots.get(basisIndex)==knots.get(basisIndex+_order))?0:
                1.0/(knots.get(basisIndex+_order)-knots.get(basisIndex));
        _left = null;
        _rite = null;
        _index = basisIndex;
    }

}
