package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class ISplines {
    private final List<Double> _knotsOriginal;    // stores knots sequence, not expanded
    private final int _order;         // order of ISplines, starts from 1, 2, ...
    private int _nKnots;        // number of knots not counting duplicates
    private int _numIBasis;     // number of I splines over knot sequence
    private int _totKnots;      // number of knots including duplicates
    private NormalizedBSplines _bSplines;   // point to BSpline of order _order+1 over the same knot sequence
    
    public ISplines(int order, double[] knots) {
        _knotsOriginal = Arrays.stream(knots).boxed().collect(Collectors.toList());
        _order = order;
        _bSplines = new NormalizedBSplines(order, knots.length, knots);
    }
    
    
    private static class ISplineBasis {
        private int _index;     // starting knot sequence index where t >= t[_index], including duplicates
        private int _lastIndex; // last knot sequence where if t > t[_lastIndex], Ispline = 1
        
    }
}
