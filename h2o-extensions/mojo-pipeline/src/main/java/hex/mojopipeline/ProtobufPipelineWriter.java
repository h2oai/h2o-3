package hex.mojopipeline;

import mojo.spec.PipelineOuterClass;
import water.api.StreamWriteOption;
import water.api.StreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProtobufPipelineWriter implements StreamWriter {

    PipelineOuterClass.Pipeline _pipeline;
    
    public ProtobufPipelineWriter(PipelineOuterClass.Pipeline pipeline) {
        _pipeline = pipeline;
    }
    @Override
    public void writeTo(OutputStream os, StreamWriteOption... options) {
        ZipOutputStream zos = new ZipOutputStream(os);
        try {
            zos.putNextEntry(new ZipEntry("mojo/"));
            zos.putNextEntry(new ZipEntry("mojo/pipeline.pb"));
            _pipeline.writeTo(zos);
            zos.closeEntry();
            zos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
