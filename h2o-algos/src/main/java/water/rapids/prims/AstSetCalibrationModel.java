package water.rapids.prims;

import hex.Model;
import hex.tree.CalibrationHelper;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValStr;

public class AstSetCalibrationModel extends AstPrimitive<AstSetCalibrationModel> {

    @Override
    public String[] args() {
        return new String[]{"model", "calibrationModel"};
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } // (set.calibration.model model calibrationModel)

    @Override
    public String str() {
        return "set.calibration.model";
    }

    @Override
    public ValStr apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model<?, ?, ?> model = (Model<?, ?, ?>) stk.track(asts[1].exec(env)).getModel();
        if (! (model._output instanceof CalibrationHelper.OutputWithCalibration)) {
            throw new IllegalArgumentException("Models of type " + model._parms.algoName() + " don't support calibration.");
        }
        Model<?, ?, ?> calibrationModel = (Model<?, ?, ?>) stk.track(asts[2].exec(env)).getModel();
        try {
            model.write_lock();
            ((CalibrationHelper.OutputWithCalibration) model._output).setCalibrationModel(calibrationModel);
            model.update();
        } finally {
            model.unlock();
        }
        return new ValStr("OK");
    }

}
