package water.rapids.ast.prims.models;

import hex.Model;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

/**
 * Ast class for passing the model, frame and metric to calculate Permutation Variable Importance
 */
public class AstPermutationVarImp extends AstPrimitive {

    @Override
    public int nargs()      { return 1 + 3; }

    @Override
    public String[] args()  { return new String[]{"model", "frame", "metric"}; }

    @Override
    public String str()     { return "PermutationVarImp"; }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame fr = stk.track(asts[2].exec(env)).getFrame();
        String metric = stk.track(asts[3].exec(env)).getStr();

        Scope.enter();
        Frame pviFr = null;
        try {
            // Calculate Permutation Variable Importance 
            TwoDimTable varImpTable = model.getPermVarImpTable(fr, metric);

            // Create Frame from TwoDimTable 
            pviFr = new Frame(Key.make(model._key + "permutationVarImp"));
            pviFr.add(headerToStrings(varImpTable.getRowHeaders()), rowsToVecs(varImpTable));
            Scope.track(pviFr);
        } finally {
            Key[] keysToKeep = pviFr != null ? pviFr.keys() : new Key[]{};
            Scope.exit(keysToKeep);
        }
        return new ValFrame(pviFr);
    }

    /**
     * TwoDimTable rows to Vecs which will be used to create a Frame
     * @param varImpTable TwoDimTable of PVI
     * @return an array of Vecs
     */
    Vec[] rowsToVecs(TwoDimTable varImpTable) {
        Vec[] vecs = new Vec[varImpTable.getRowDim() + 1];
        // Relative, scaled, and percentage importance
        vecs[0] = Vec.makeVec(varImpTable.getColHeaders(), Vec.newKey());
        double[] tmp_row = new double[varImpTable.getColDim()];
        for (int i = 0; i < varImpTable.getRowDim(); i++) {
            for (int j = 0; j < varImpTable.getColDim(); j++) {
                tmp_row[j] = (double) varImpTable.get(i, j);
            }
            vecs[i + 1] = Vec.makeVec(tmp_row, Vec.newKey());
        }
        return vecs;
    }

    /**
     * TwoDimTable headers to an array of strings with the first element being the importance 
     * @param tableNames TwoDimTable of PVI
     * @return an array of Strings
     */
    String[] headerToStrings(String[] tableNames) {
        String[] varNames = new String[tableNames.length + 1];
        varNames[0] = "importance";
        System.arraycopy(tableNames, 0, varNames, 1, tableNames.length);
        // add column containing Strings: Relative importance, Scaled importance, percentage 
        return varNames;
    }
}
