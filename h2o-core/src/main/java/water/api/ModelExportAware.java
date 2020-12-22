package water.api;

import hex.ModelExportOption;

public interface ModelExportAware {

    boolean isExportCVPredictionsEnabled(); 

    default ModelExportOption[] getModelExportOptions() {
        if (isExportCVPredictionsEnabled())
            return new ModelExportOption[]{ModelExportOption.INCLUDE_CV_PREDICTIONS};
        else
            return null;
    }
    
}
