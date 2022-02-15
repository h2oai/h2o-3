package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.List;

public class NBSplinesTypeI {
    private final int _order;
    private final int _numKnots;
    private List<Double> coeffs;
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
        _order= order-1;
        _numKnots = knots.size();
        _knots = new ArrayList<>(knots.subList(basisIndex, basisIndex+order));
        _commonConst = (knots.get(basisIndex)==knots.get(basisIndex+_order))?0:
                1.0/(knots.get(basisIndex+_order)-knots.get(basisIndex));
        _left = null;
        _rite = null;
    }

    /***
     * 
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] gen_penalty_matrix(double[] knots, int order) {
        int numBasis = knots.length+2*order-2;
        int totKnots = knots.length+order-2;
        double[][] penaltyMat = new double[numBasis][totKnots];
        // 
        return penaltyMat;
    }
}
