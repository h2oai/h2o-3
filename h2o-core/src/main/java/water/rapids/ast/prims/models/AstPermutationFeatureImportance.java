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

public class AstPermutationFeatureImportance extends AstPrimitive{

    @Override
    public int nargs() {return 1 + 3;} // Perm_feature_importance + Frame + Model

    @Override
    public String[] args() {return new String [] {"frame", "model", "metric"};} 
    
    @Override
    public String str() {return "PermutationVarImp";} 
    
    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame in_fr = stk.track(asts[2].exec(env)).getFrame();
        String metric = stk.track(asts[3].exec(env)).getStr();
        
        Scope.enter();
        Frame pred_fr = null;
        Frame FiFrame = null;
        try {
            pred_fr = model.score(in_fr); // prediction on original dataset
            Scope.track(pred_fr);

            TwoDimTable varImp_t = model.getPermVarImpTable(in_fr, metric);
            
            // Frame contains one row and n features 
            FiFrame = new Frame(Key.make(model._key + "_feature_imp"));

            PermutationFeatureImportanceAstHelper filler = new PermutationFeatureImportanceAstHelper();
            Vec [] vecs = filler.do_Vecs(varImp_t);
            String [] names = filler.do_Names(varImp_t.getRowHeaders());

            FiFrame.add(names, vecs);
            Scope.track(FiFrame);
        } finally {
            Key[] keysToKeep = FiFrame != null ? FiFrame.keys() : new Key[]{};
            Key[] keysToKeepPred = pred_fr != null ? pred_fr.keys() : new Key[]{};
            Scope.exit(keysToKeep);
            Scope.exit(keysToKeep_);              
        }
        return new ValFrame(FiFrame);
    }
    
}
