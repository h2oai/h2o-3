package water.rapids.ast.prims.models;


import hex.Model;
import water.rapids.Env;
import water.rapids.FeatureImportance4BBM;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstFeatureImportance4BBM extends AstPrimitive {

    @Override
    public int nargs() {return 1 + 1;}

    @Override
    public String[] args() {return new String [] {"Model"};} // 
    
    @Override
    public String str() {return "FI4BBM";} // uncertain
    
    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]){
        Model model = stk.track(asts[1].exec(env)).getModel();
        FeatureImportance4BBM fi = new FeatureImportance4BBM(model);
        return new ValFrame(fi._ouput);

//        return new ValFrame(models.toFrame());
    }
}
