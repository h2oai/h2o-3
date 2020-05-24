package water.rapids.ast.prims.models;


import hex.Model;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.FeatureImportance4BBM;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.Map;
import java.util.Map.Entry;

import java.util.HashMap;

public class AstFeatureImportance4BBM extends AstPrimitive {

    @Override
    public int nargs() {return 1 + 2;} // Perm_feature_importance + Modle + Frame

    @Override
    public String[] args() {return new String [] {"model", "frame"};} 
    
    @Override
    public String str() {return "Perm_Feature_importance";} // or call it model_reliance 
    
    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame fr = stk.track(asts[2].exec(env)).getFrame();
        
        FeatureImportance4BBM fi = new FeatureImportance4BBM(model, fr , null, model._parms._response_column);
        HashMap<String, Double> FI = fi.getFeatureImportance(); // might be able to use AstPerfectAuc for calculation of AUC 

        // Frame contains one row and n features 
        Frame outfr = new Frame(Key.make("feature_imp")); 
        
        for (Map.Entry<String, Double> entry : FI.entrySet()) {
            outfr.add( entry.getKey(),Vec.makeVec(new double [] {entry.getValue()}, Vec.newKey()));
            System.out.println(entry.getKey() + "/" + entry.getValue());
        }
        
        return new ValFrame(outfr);            
    }
}
