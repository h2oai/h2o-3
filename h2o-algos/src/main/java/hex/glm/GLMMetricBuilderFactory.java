package hex.glm;

import com.google.gson.JsonObject;
import hex.ModelMetricsSupervised;
import hex.MultinomialAucType;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.glm.GlmMojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.ModelJsonReader;

public class GLMMetricBuilderFactory extends ModelMetricsSupervised.SupervisedMetricBuilderFactory<GLMModel, GlmMojoModel> {

    @Override
    public Object extractExtraInfo(GLMModel binaryModel) {
        return new Object() {
            double[] ymu = binaryModel._output._ymu;
            int rank = binaryModel._output.rank();
        }; 
    }
    
    @Override
    public IMetricBuilder createBuilder(GlmMojoModel mojoModel, JsonObject extraInfo) {
        if (extraInfo == null) {
            throw new RuntimeException("The mojo model doesn't support calculation of model metrics without H2O runtime, since extra info is missing.");
        }

        int rank = extraInfo.getAsJsonPrimitive("rank").getAsInt();
        double[] ymu = ModelJsonReader.readDoubleArray(extraInfo.getAsJsonArray("ymu"));

        ModelAttributes attributes =  mojoModel._modelAttributes;
        
        String familyString = (String)attributes.getParameterValueByName("family");
        GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.valueOf(familyString);
        String linkString = (String)attributes.getParameterValueByName("link");
        GLMModel.GLMParameters.Link link = GLMModel.GLMParameters.Link.valueOf(linkString);
        Double variancePower = (Double)attributes.getParameterValueByName("tweedie_variance_power");
        Double linkPower = (Double)attributes.getParameterValueByName("tweedie_link_power");
        Double theta = (Double)attributes.getParameterValueByName("theta");
        GLMModel.GLMWeightsFun glmf = new GLMModel.GLMWeightsFun(family, link, variancePower, linkPower, theta);

        String aucTypeString = (String)attributes.getParameterValueByName("auc_type");
        MultinomialAucType aucType = MultinomialAucType.valueOf(aucTypeString);
        Boolean hglm = (Boolean)attributes.getParameterValueByName("HGLM");
        Boolean intercept = (Boolean)attributes.getParameterValueByName("intercept");

        String responseColumn = mojoModel._responseColumn;
        String[] responseDomain = mojoModel.getDomainValues(responseColumn);
        
        return new IndependentGLMMetricBuilder(responseDomain, ymu, glmf, rank, true, intercept, aucType, hglm);
    }
}
