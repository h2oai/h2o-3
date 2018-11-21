package hex.genmodel.algos.svm;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class SvmMojoReader extends ModelMojoReader<SvmMojoModel> {

    @Override
    public String getModelName() {
        return "SVM";
    }

    @Override
    protected void readModelData() throws IOException {
        _model.meanImputation = readkv("meanImputation");

        if(_model.meanImputation) {
            _model.means = readkv("means");
        }

        _model.weights = readkv("weights");
        _model.interceptor = readkv("interceptor");
        _model.defaultThreshold = readkv("defaultThreshold");
        _model.threshold = readkv("threshold");
    }

    @Override
    protected SvmMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
        return new SvmMojoModel(columns, domains, responseColumn);
    }

    @Override public String mojoVersion() {
        return "1.00";
    }
}

