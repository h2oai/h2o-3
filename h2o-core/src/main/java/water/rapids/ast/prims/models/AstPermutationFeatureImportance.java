package water.rapids.ast.prims.models;


import hex.Model;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.PermutationFeatureImportance;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;


import java.util.Map;
import java.util.Map.Entry;

import java.util.HashMap;

public class AstPermutationFeatureImportance extends AstPrimitive {

    @Override
    public int nargs() {return 1 + 2;} // Perm_feature_importance + Frame + Model

    @Override
    public String[] args() {return new String [] {"frame", "model"};} 
    
    @Override
    public String str() {return "Perm_Feature_importance";} // or call it model_reliance 
    
    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Frame fr = stk.track(asts[1].exec(env)).getFrame();
        Model model = stk.track(asts[2].exec(env)).getModel();

        Frame fr2 = model.score(fr); // prediction on original dataset
        
//      model._train.get(); TODO find a way to avoid passing the frame and get it from the model instead

        PermutationFeatureImportance fi = new PermutationFeatureImportance(model, fr, fr2);
        HashMap<String, Double> FI = fi.getFeatureImportance(); // might be able to use AstPerfectAuc for calculation of AUC 

        TwoDimTable fi_table = model.getTable(fr, fr2);
        
        // Frame contains one row and n features 
        Frame outfr = new Frame(Key.make("feature_imp")); 
        
        for (Map.Entry<String, Double> entry : FI.entrySet()) {
            outfr.add( entry.getKey(),Vec.makeVec(new double [] {entry.getValue()}, Vec.newKey()));
            System.out.println(entry.getKey() + "/" + entry.getValue());
        }
        
        return new ValFrame(outfr);            
    }
}
