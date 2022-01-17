package hex.rulefit;

import com.google.gson.JsonObject;
import hex.MultinomialAucType;
import hex.gam.GAMModel;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.gam.GamMojoModelBase;
import hex.genmodel.algos.glm.GlmMojoModel;
import hex.genmodel.algos.rulefit.RuleFitMojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.ModelJsonReader;
import hex.glm.GLMMetricBuilderFactory;
import hex.glm.GLMMetricBuilderFactoryBase;
import hex.glm.GLMModel;
import hex.glm.IndependentGLMMetricBuilder;

public class RuleFitMetricBuilderFactory extends GLMMetricBuilderFactoryBase<RuleFitModel, RuleFitMojoModel> {

    private GLMMetricBuilderFactory glmFactory = new GLMMetricBuilderFactory();
    
    @Override
    public GLMMetricExtraInfo extractExtraInfo(RuleFitModel binaryModel) {
        return glmFactory.extractExtraInfo(binaryModel.glmModel);
    }

    @Override
    public IMetricBuilder createBuilder(RuleFitMojoModel mojoModel, JsonObject extraInfo) {
        return glmFactory.createBuilder((GlmMojoModel)mojoModel._linearModel, extraInfo);
    }
}
