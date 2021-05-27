package water.rapids.ast.prims.models;

import hex.Model;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.PermutationVarImp;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

import java.util.Locale;

/**
 * Ast class for passing the model, frame and metric to calculate Permutation Variable Importance
 */
public class AstPermutationVarImp extends AstPrimitive {

    @Override
    public int nargs()      { return 1 + 7; }

    @Override
    public String[] args()  { return new String[]{"model", "frame", "metric", "n_samples", "n_repeats", "features", "seed"}; }

    @Override
    public String str()     { return "PermutationVarImp"; }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame fr = stk.track(asts[2].exec(env)).getFrame();
        String metric = stk.track(asts[3].exec(env)).getStr().toLowerCase();
        long n_samples = (long) stk.track(asts[4].exec(env)).getNum();
        int n_repeats = (int) stk.track(asts[5].exec(env)).getNum();
        String[] features = null;
        Val featuresVal = stk.track(asts[6].exec(env));
        if (!featuresVal.isEmpty()) // empty string list is interpreted as nums
            features = featuresVal.getStrs();
        long seed = (long) stk.track(asts[7].exec(env)).getNum();

        Scope.enter();
        Frame pviFr = null;
        try {
            // Calculate Permutation Variable Importance
            PermutationVarImp pvi = new PermutationVarImp(model, fr);
            TwoDimTable varImpTable = null;
            if (n_repeats > 1) {
                varImpTable = pvi.getRepeatedPermutationVarImp(metric, n_samples, n_repeats, features, seed);
            } else {
                varImpTable = pvi.getPermutationVarImp(metric, n_samples, features, seed);
            }
            // Create Frame from TwoDimTable 
            pviFr = varimpToFrame(varImpTable, Key.make(model._key + "permutationVarImp"));
            Scope.track(pviFr);
        } finally {
            Key[] keysToKeep = pviFr != null ? pviFr.keys() : new Key[]{};
            Scope.exit(keysToKeep);
        }
        return new ValFrame(pviFr);
    }
    private static Frame varimpToFrame(TwoDimTable twoDimTable, Key frameKey) {
        String[] colNames = new String[twoDimTable.getColDim() + 1];
        colNames[0] = "Variable";
        System.arraycopy(twoDimTable.getColHeaders(),0, colNames, 1, twoDimTable.getColDim());

        Vec[] vecs = new Vec[colNames.length];
        vecs[0] = Vec.makeVec(twoDimTable.getRowHeaders(), Vec.newKey());

        double[] tmpRow = new double[twoDimTable.getRowDim()];
        for (int j = 0; j < twoDimTable.getColDim(); j++) {
            for (int i = 0; i < twoDimTable.getRowDim(); i++) {
                tmpRow[i] = (double) twoDimTable.get(i, j);
            }
            vecs[j + 1] = Vec.makeVec(tmpRow, Vec.newKey());
        }
        Frame fr = new Frame(frameKey, colNames, vecs);
        return fr;
    }

}
