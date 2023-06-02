package hex.api;

import hex.mojopipeline.H2OAssemblyToMojoPipelineConverter;
import hex.mojopipeline.ProtobufPipelineWriter;
import mojo.spec.PipelineOuterClass;
import water.DKV;
import water.api.Handler;
import water.api.StreamingSchema;
import water.api.schemas99.AssemblyV99;
import water.rapids.Assembly;

public class AssemblyToMojoPipelineExportHandler extends Handler {
  @SuppressWarnings("unused")
  public StreamingSchema fetchMojoPipeline(final int version, final AssemblyV99 ass) {
    Assembly assembly = DKV.getGet(ass.assembly_id);
    if (assembly == null) {
      throw new IllegalArgumentException("Assembly doesn't exist in DKV. It must be fitted first.");
    }
    PipelineOuterClass.Pipeline pipeline = H2OAssemblyToMojoPipelineConverter.convertToProtoBufPipeline(assembly);
    return new StreamingSchema(new ProtobufPipelineWriter(pipeline), ass.file_name + ".mojo");
  }
}
