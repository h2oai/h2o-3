package hex.glm;

import hex.genmodel.algos.glm.GlmMojoModelBase;

public class GLMMetricBuilderFactory extends GLMMetricBuilderFactoryBase<GLMModel, GlmMojoModelBase> {

    @Override
    public GLMMetricExtraInfo extractExtraInfo(GLMModel binaryModel) {
        GLMMetricExtraInfo extraInfo = new GLMMetricExtraInfo();
        extraInfo.ymu = binaryModel._output.ymu();
        extraInfo.rank = binaryModel._output.rank();
        return extraInfo;
    }
}
