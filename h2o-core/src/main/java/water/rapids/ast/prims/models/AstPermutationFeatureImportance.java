package water.rapids.ast.prims.models;


import hex.Model;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.PermutationFeatureImportance;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

public class AstPermutationFeatureImportance extends AstPrimitive{

    @Override
    public int nargs() {return 1 + 2;} // Perm_feature_importance + Frame + Model

    @Override
    public String[] args() {return new String [] {"frame", "model"};} 
    
    @Override
    public String str() {return "Perm_Feature_importance";} // or call it model_reliance 
    
    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Frame in_fr = stk.track(asts[1].exec(env)).getFrame();
        Model model = stk.track(asts[2].exec(env)).getModel();

        Scope.enter();
        Frame pred_fr = null;
        Frame FiFrame = null;
        try {
            pred_fr = model.score(in_fr); // prediction on original dataset
            Scope.track(pred_fr);

            PermutationFeatureImportance fi = new PermutationFeatureImportance(model, in_fr, pred_fr);
//            HashMap<String, Double> FI = fi.getFeatureImportance(); // might be able to use AstPerfectAuc for calculation of AUC 

            TwoDimTable varImp_t = model.getPermVarImpTable_oat(in_fr, pred_fr);
            
            // Frame contains one row and n features 
            FiFrame = new Frame(Key.make(model._key + "_feature_imp"));

            PermutationFeatureImportanceAstHelper filler = new PermutationFeatureImportanceAstHelper();
            Vec [] vecs = filler.do_Vecs(varImp_t);
            String [] names = filler.do_Names(varImp_t.getRowHeaders());

            FiFrame.add(names, vecs);
            Scope.track(FiFrame);
        } finally {
            Key[] keysToKeep = FiFrame != null ? FiFrame.keys() : new Key[]{};
            Key[] keysToKeep_ = pred_fr != null ? pred_fr.keys() : new Key[]{};
            Scope.exit(keysToKeep);
            Scope.exit(keysToKeep_);              
        }
        return new ValFrame(FiFrame);
    }
    
}
