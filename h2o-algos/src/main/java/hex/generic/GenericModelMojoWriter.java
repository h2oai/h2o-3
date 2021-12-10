package hex.generic;

import hex.ModelMetrics;
import hex.ModelMojoWriter;
import hex.genmodel.utils.IOUtils;
import water.api.StreamWriteOption;
import water.fvec.ByteVec;
import water.util.Log;

import java.io.*;

public class GenericModelMojoWriter extends ModelMojoWriter<GenericModel, GenericModelParameters, GenericModelOutput> {

    private ByteVec _mojoBytes;

    @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
    public GenericModelMojoWriter() {
    }

    public GenericModelMojoWriter(ByteVec _mojoBytes) {
        this._mojoBytes = _mojoBytes;
    }


    @Override
    public String mojoVersion() {
        return "1.00";
    }

    @Override
    protected void writeModelData() throws IOException {
        // Do nothing on purpose
    }

    @Override
    public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() { return null; }

    @Override
    public void writeTo(final OutputStream os, StreamWriteOption... options) {
        try (final InputStream inputStream = _mojoBytes.openStream(null); OutputStream outputStream = os) {
            IOUtils.copyStream(inputStream, outputStream);
        } catch (IOException e) {
            Log.throwErr(e);
        }
    }
}
