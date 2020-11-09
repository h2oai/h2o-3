package water.rapids.ast.prims.models;

import hex.Model;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.PermutationVarImp;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

/**
 * Ast class for passing the model and frame such that the OAT Morris table can be created
 */
public class AstOneAtaTimeMorris extends AstPrimitive {

    @Override
    public int nargs() {return 1 + 2;} // Perm_feature_importance + Frame + Model

    @Override
    public String[] args() {return new String [] {"model", "frame"};}

    @Override
    public String str() {return "PermutationVarImpOat";} 

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame in_fr = stk.track(asts[2].exec(env)).getFrame();

        Scope.enter();
        Frame pviFr = null;
        try {
            PermutationVarImp fi = new PermutationVarImp(model, in_fr); 
            TwoDimTable oatTable = fi.oat(); // might be able to use AstPerfectAuc for calculation of AUC 
            // Get OAT TwoDimTable 
            
            // Frame contains one row and n features 
            pviFr = new Frame(Key.make(model._key + "Oat_pfi"));
            pviFr.add(headerToStrings(oatTable.getRowHeaders()), rowsToVecs(oatTable));
            Scope.track(pviFr);
            
            Scope.track(pviFr);
        } finally {
            Key[] keysToKeep = pviFr != null ? pviFr.keys() : new Key[]{};
            Scope.exit(keysToKeep);
        }
        return new ValFrame(pviFr);
    }

    /**
     * TwoDimTable rows to Vecs which will be used to create a Frame
     * @param varImp_t TwoDimTable of PVI
     * @return an array of Vecs
     */
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

    /**
     * TwoDimTable headers to an array of strings with the first element being the indices
     * @param table_names TwoDimTable of PVI
     * @return an array of Strings
     */
    String[] headerToStrings(String[] table_names) {
        String[] varNames = new String[table_names.length + 1];
        varNames[0] = "indices";
        System.arraycopy(table_names, 0, varNames, 1, table_names.length);
        // add column containing Strings: Relative importance, Scaled importance, percentage 
        return varNames;
    }
}
