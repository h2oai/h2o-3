package hex.glm;

import com.google.gson.JsonObject;
import hex.Model;
import hex.ModelMetricsSupervised;
import hex.MultinomialAucType;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.ModelJsonReader;

public abstract class GLMMetricBuilderFactoryBase<TModel extends Model, TMOJOModel extends MojoModel>
        extends ModelMetricsSupervised.SupervisedMetricBuilderFactory<TModel, TMOJOModel> {
    public static class GLMMetricExtraInfo {
        public double[] ymu;
        public int rank;
    }
    
    @Override
    public IMetricBuilder createBuilder(TMOJOModel mojoModel, JsonObject extraInfo) {
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
        Object hglmObject = attributes.getParameterValueByName("HGLM");
        Boolean hglm = false;
        if (hglmObject != null) {
            hglm = (Boolean)hglmObject;
        }
        Boolean intercept = (Boolean)attributes.getParameterValueByName("intercept");

        String responseColumn = mojoModel._responseColumn;
        String[] responseDomain = mojoModel.getDomainValues(responseColumn);

        return new IndependentGLMMetricBuilder(responseDomain, ymu, glmf, rank, true, intercept, aucType, hglm);
    }
}
