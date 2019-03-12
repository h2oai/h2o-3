package hex.mojo;

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
    public void writeTo(OutputStream os) {
        try(final InputStream inputStream = _mojoBytes.openStream(null)) {
            IOUtils.copyStream(inputStream,os);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                Log.err(e);
            }
        }
    }
}
