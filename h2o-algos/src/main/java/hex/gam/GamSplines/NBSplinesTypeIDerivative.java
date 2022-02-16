package hex.gam.GamSplines;

import org.apache.commons.math3.geometry.partitioning.BSPTreeVisitor;

import java.util.ArrayList;
import java.util.List;

import static hex.gam.GamSplines.NBSplinesUtils.extractKnots;
import static hex.gam.GamSplines.NBSplinesUtils.fillKnots;

public class NBSplinesTypeIDerivative {
    /***
     * This class implements the derivative of NBSpline Type I.  
     */
    private final int _order;
    private final int _index;
    private final List<Double> _knots;
    private final double _commonConst;
    private double[] _leftCoeff;
    private double[] _rightCoeff;
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;
    
    
    public NBSplinesTypeIDerivative(int order, int basisIndex, List<Double> knots) {
        _order = order;
        _index = basisIndex;
        _knots = extractKnots(_index, order, knots);;
        double part1 = (_order-1)==0 ? 0 : _order/(_order-1.0);
        _commonConst = part1*((_knots.get(_order) == _knots.get(0)) ? 0 : 1.0/(_knots.get(_order)-_knots.get(0)));
        _leftCoeff = new double[]{-_knots.get(0),1};
        _rightCoeff = new double[]{_knots.get(_order), -1};
        
    }


    /***
     *
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] genPenaltyMatrix(double[] knots, int order) {
        int numBasis = knots.length+2*order-2;
        int totKnots = knots.length+order-2;
        List<Double> knotsWithDuplicates = fillKnots(knots, order, false);
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i=0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {

            }
        }
        // 
        return penaltyMat;
    }
}
