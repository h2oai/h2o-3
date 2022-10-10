package hex.glm;

import hex.DataInfo;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.stream.DoubleStream;

/***
 * class to find bounds on infinite series approximation to calculate tweedie dispersion parameter using the 
 * maximum likelihood function in Dunn et.al. in Series evalatuino of Tweedie exponential dispersion model 
 * densities, statistics and computing, Vol 15, 2005.
 */
public class TweedieMLDispersionOnly {
    double _dispersionParameter;   // parameter of optimization
    final double _variancePower;
    int _constNCol;
    int _nWorkingCol = 0;
    Frame _infoFrame;   // contains response, mu, weightColumn, constants, max value index, ...
    Frame _mu;
    final String[] _constFrameNames;
    String[] _workFrameNames;
    boolean _weightPresent;
    int _indexBound;    // denotes maximum index we are willing to try
    int _nWVs = 3;
    boolean[] _computationAccuracy; // set to false when upper bound exceeds _indexBound
    boolean _debugOn;

    public TweedieMLDispersionOnly(Frame train, GLMModel.GLMParameters parms, GLMModel model, double[] beta,
                                   DataInfo dinfo) {
        _variancePower = parms._tweedie_variance_power;
        _dispersionParameter = parms._init_dispersion_parameter;
        _constFrameNames = new String[]{"jMaxConst", "zConst", "part2Const", "oneOverY", "oneOverPiY",
                "firstOrderDerivConst", "secondOrderDerivConst"};
        _constNCol = _constFrameNames.length;
        DispersionTask.GenPrediction gPred = new DispersionTask.GenPrediction(beta, model, dinfo).doAll(
                1, Vec.T_NUM, dinfo._adaptedFrame);
        _mu = gPred.outputFrame(Key.make(), new String[]{"prediction"}, null);  // generate prediction
        DKV.put(_mu);
        // form info frame which contains response, mu and weight column if specified
        _infoFrame = formInfoFrame(train, _mu, parms);
        DKV.put(_infoFrame);
        // generate constants used during dispersion parameter update
        DispersionTask.ComputeTweedieConstTsk _tweedieConst = new DispersionTask.ComputeTweedieConstTsk(_variancePower, _infoFrame);
        _tweedieConst.doAll(_constNCol, Vec.T_NUM, _infoFrame);
        _infoFrame.add(Scope.track(_tweedieConst.outputFrame(Key.make(), _constFrameNames, null)));
        _debugOn = parms._debugTDispersionOnly;
        if (_debugOn) { // only expand frame when debug is turned on
            _workFrameNames = new String[]{"jOrKMax", "logZ", "_WOrVMax", "dWOrVMax", "d2WOrVMax", "jOrkL", "jOrkU",
                    "djOrkL", "djOrkU", "d2jOrkL", "d2jOrKU", "sumWV", "sumDWV", "sumD2WV", "ll", "dll", "d2ll"};
            _nWorkingCol = _workFrameNames.length;
            Vec[] vecs = _infoFrame.anyVec().makeDoubles(_nWorkingCol, DoubleStream.generate(()
                    -> Math.random()).limit(_nWorkingCol).map(x -> 0.0).toArray());
            _infoFrame.add(_workFrameNames, vecs);
            DKV.put(_infoFrame);
        }
        _weightPresent = parms._weights_column != null;
        _indexBound = parms._max_series_index;
        _computationAccuracy = new boolean[_nWVs];
    }
    
    public static Frame formInfoFrame(Frame train, Frame mu, GLMModel.GLMParameters parms) {
        Frame infoFrame = new Frame(Key.make());
        String[] colNames;
        Vec[] vecs;
        if (parms._weights_column != null) {
            colNames = new String[]{parms._response_column, mu.names()[0], parms._weights_column};
            vecs = new Vec[]{train.vec(parms._response_column), mu.vec(0), train.vec(parms._weights_column)};
        } else {
            colNames = new String[]{parms._response_column, mu.names()[0]};
            vecs = new Vec[]{train.vec(parms._response_column), mu.vec(0)};
        }
        infoFrame.add(colNames, vecs);
        return infoFrame;
    }
    
    public void updateDispersionP(double phi) {
        _dispersionParameter = phi;
    }
    
    public void cleanUp() {
        DKV.remove(_mu._key);
        DKV.remove(_infoFrame._key);
    }
}
