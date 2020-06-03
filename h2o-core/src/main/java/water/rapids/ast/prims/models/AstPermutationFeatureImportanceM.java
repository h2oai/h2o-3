package water.rapids.ast.prims.models;

import hex.Model;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.PermutationFeatureImportance;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

import java.util.HashMap;
import java.util.Map;

public class AstPermutationFeatureImportanceM extends AstPrimitive {
    @Override
    public int nargs() {return 1 + 1;} // Perm_feature_importance + Model

    @Override
    public String[] args() {return new String [] {"model"};}

    @Override
    public String str() {return "Perm_Feature_importance_m";} // 
    
    @Override
    public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Model model = stk.track(asts[2].exec(env)).getModel();

        Scope.enter();
        Frame FiFrame = null;
        try {
            HashMap<String, Double>  FI = model.permutationFeatureImportance(model); // prediction on original dataset

            // Frame contains one row and n features 
            FiFrame = new Frame(Key.make(model._key + "_feature_imp"));
            for (Map.Entry<String, Double> entry : FI.entrySet()) {
                FiFrame.add( entry.getKey(), Vec.makeVec(new double [] {entry.getValue()}, Vec.newKey()));
                System.out.println(entry.getKey() + "/" + entry.getValue());
            }
            Scope.track(FiFrame);

        } finally {
            Key[] keysToKeep = FiFrame != null ? FiFrame.keys() : new Key[]{};
            Scope.exit(keysToKeep);
        }
        
        return new ValFrame(FiFrame);
    }
}
