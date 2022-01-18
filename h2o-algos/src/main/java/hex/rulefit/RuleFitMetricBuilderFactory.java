package hex.rulefit;

import com.google.gson.JsonObject;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.glm.GlmMojoModelBase;
import hex.genmodel.algos.rulefit.RuleFitMojoModel;
import hex.glm.GLMMetricBuilderFactory;
import hex.glm.GLMMetricBuilderFactoryBase;

public class RuleFitMetricBuilderFactory extends GLMMetricBuilderFactoryBase<RuleFitModel, RuleFitMojoModel> {

    private GLMMetricBuilderFactory glmFactory = new GLMMetricBuilderFactory();
    
    @Override
    public GLMMetricExtraInfo extractExtraInfo(RuleFitModel binaryModel) {
        return glmFactory.extractExtraInfo(binaryModel.glmModel);
    }

    @Override
    public IMetricBuilder createBuilder(RuleFitMojoModel mojoModel, JsonObject extraInfo) {
        return glmFactory.createBuilder((GlmMojoModelBase) mojoModel._linearModel, extraInfo);
    }
}
