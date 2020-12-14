package water.api;

import hex.ModelExportOptions;

public interface ModelExportAware {

    boolean isExportCVPredictionsEnabled(); 

    default ModelExportOptions[] getModelExportOptions() {
        if (isExportCVPredictionsEnabled())
            return new ModelExportOptions[]{ModelExportOptions.INCLUDE_CV_PREDICTIONS};
        else
            return null;
    }
    
}
