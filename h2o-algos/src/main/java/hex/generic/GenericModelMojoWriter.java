package hex.generic;

import hex.ModelMojoWriter;
import hex.genmodel.utils.IOUtils;
import water.fvec.ByteVec;
import water.util.Log;

import java.io.*;

public class GenericModelMojoWriter extends ModelMojoWriter<GenericModel, GenericModelParameters, GenericModelOutput> {

    private final ByteVec _mojoBytes;

    public GenericModelMojoWriter(ByteVec _mojoBytes) {
        this._mojoBytes = _mojoBytes;
    }


    @Override
    public String mojoVersion() {
        return "1.0";
    }

    @Override
    protected void writeModelData() throws IOException {
        // Do nothing on purpose
    }

    @Override
    public void writeTo(final OutputStream os) {
        try (final InputStream inputStream = _mojoBytes.openStream(null); OutputStream outputStream = os) {
            IOUtils.copyStream(inputStream, outputStream);
        } catch (IOException e) {
            Log.throwErr(e);
        }
    }
}
