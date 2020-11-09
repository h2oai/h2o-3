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
 * 
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

    // Rows of TwoDimTable to a Vec [] 
    Vec[] rowsToVecs(TwoDimTable varImp_t) {
        Vec[] vecs = new Vec[varImp_t.getRowDim() + 1];
        // Relative, scaled, and percentage importance
        vecs[0] = Vec.makeVec(varImp_t.getColHeaders(), Vec.newKey());
        double[] tmp_row = new double[varImp_t.getColDim()];
        for (int i = 0; i < varImp_t.getRowDim(); i++) {
            for (int j = 0; j < varImp_t.getColDim(); j++) {
                tmp_row[j] = (double) varImp_t.get(i, j); 
            }
            vecs[i + 1] = Vec.makeVec(tmp_row, Vec.newKey());
        }
        return vecs;
    }

    // Features to String []
    String[] headerToStrings(String[] table_names) {
        String[] varNames = new String[table_names.length + 1];
        varNames[0] = "ID";
        System.arraycopy(table_names, 0, varNames, 1, table_names.length);
        // add column containing Strings: Relative importance, Scaled importance, percentage 
        return varNames;
    }
}
