package hex.gam;

import hex.genmodel.algos.gam.GamMojoModelBase;
import hex.glm.GLMMetricBuilderFactoryBase;

public class GAMMetricBuilderFactory extends GLMMetricBuilderFactoryBase<GAMModel, GamMojoModelBase> {

    @Override
    public GLMMetricExtraInfo extractExtraInfo(GAMModel binaryModel) {
        GLMMetricExtraInfo extraInfo = new GLMMetricExtraInfo();
        extraInfo.ymu = binaryModel._ymu;
        extraInfo.rank = binaryModel._rank;
        return extraInfo;
    }
}
