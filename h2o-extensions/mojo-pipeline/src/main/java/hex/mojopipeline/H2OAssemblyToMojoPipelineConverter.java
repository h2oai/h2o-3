package hex.mojopipeline;

import hex.genmodel.mojopipeline.transformers.MathBinaryTransform;
import hex.genmodel.mojopipeline.transformers.MathUnaryTransform;
import mojo.spec.Custom;
import mojo.spec.PipelineOuterClass;
import water.fvec.ByteVec;
import water.fvec.NFSFileVec;
import water.rapids.Assembly;
import water.rapids.ast.AstExec;
import water.rapids.transforms.H2OColOp;
import water.rapids.transforms.H2OColSelect;
import water.rapids.transforms.Transform;
import mojo.spec.PipelineOuterClass.Pipeline;
import mojo.spec.PipelineOuterClass.Transformation;
import mojo.spec.PipelineOuterClass.Frame;
import mojo.spec.ColumnOuterClass.Column;
import mojo.spec.ColumnOuterClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class H2OAssemblyToMojoPipelineConverter {
    
    public static Pipeline convertToProtoBufPipeline(Assembly assembly) {
        final Transform[] stages = assembly.steps();
        final Transform firstStage = stages[0];
        final Transform lastStage = stages[stages.length - 1];
        
        Pipeline.Builder pipelineBuilder = Pipeline.newBuilder();
        Column[] inputColumns = convertColumns(firstStage.getInputNames(), firstStage.getInputTypes());
        pipelineBuilder.setFeatures(frame(inputColumns));

        Frame.Builder interimsFrameBuilder = Frame.newBuilder();
        for (Transform stage : stages) {
            Transformation transformation = convertStage(stage);
            pipelineBuilder.addTransformations(transformation);
            if (stage.getNewNames().length > 0) {
                Column[] tempColumns = convertColumns(stage.getNewNames(), stage.getNewTypes());
                interimsFrameBuilder.addAllColumns(Arrays.asList(tempColumns));
            }
        }
        pipelineBuilder.setInterims(interimsFrameBuilder);
        setOutputColumns(pipelineBuilder, lastStage);
        Pipeline pipeline = pipelineBuilder.build();

        return pipeline;
    }
    
    public static MojoPipeline convert(Assembly assembly) throws IOException {
        Pipeline pipeline = convertToProtoBufPipeline(assembly);
        File tempFile = File.createTempFile("Pipeline", ".mojo");
        tempFile.deleteOnExit();
        ProtobufPipelineWriter writer = new ProtobufPipelineWriter(pipeline);
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            writer.writeTo(outputStream);
        }
        ByteVec mojoData = NFSFileVec.make(tempFile);
        return new MojoPipeline(mojoData);
    }

    private static Column convertColumn(String name, String type) {
        Column.Builder builder = Column.newBuilder();
        builder = builder.setName(name);
        if (type.equals("Numeric")) {
            builder.setFloat64Type(ColumnOuterClass.Float64Type.newBuilder().build());
        } else {
            builder.setStrType(ColumnOuterClass.StrType.newBuilder().build());
        }
        return builder.build();
    }

    private static Column[] convertColumns(String[] names, String[] types) {
        if (names.length != types.length) {
            throw new IllegalArgumentException(
                String.format("The length of names and types must be the same, " +
                        "but length of names is %d and length of types is %d.", names.length,  types.length));
        }
        Column[] result = new Column[names.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = convertColumn(names[i], types[i]);
        }
        return result;
    }

    private static Transformation convertStage(Transform stage){
        if (stage instanceof H2OColSelect) {
            return convertColSelect((H2OColSelect)stage);
        } else if (stage instanceof H2OColOp) {
            return convertColOp((H2OColOp)stage);
        } else {
            throw new UnsupportedOperationException(
                String.format("Stage conversion of type %s is not supported yet.", stage.getClass().getName()));
        }
    }

    private static Transformation convertColSelect(H2OColSelect stage){
        Transformation.Builder builder = Transformation.newBuilder();
        builder.setIdentityOp(PipelineOuterClass.IdentityOp.newBuilder());
        for (String outputColumn : stage.getOutputNames()) {
            builder.addInputs(outputColumn);
            builder.addOutputs(outputColumn);
        }
        return builder.build();
    }

    private static void setOutputColumns(Pipeline.Builder pipelineBuilder, Transform lastStage){
        Transformation.Builder builder = Transformation.newBuilder();
        builder.setIdentityOp(PipelineOuterClass.IdentityOp.newBuilder());
        for (String outputColumn : lastStage.getOutputNames()) {
            builder.addInputs(outputColumn);
            builder.addOutputs("assembly_" + outputColumn);
        }
        Transformation extraIdentity = builder.build();
        pipelineBuilder.addTransformations(extraIdentity);
        Column[] outputColumns = convertColumns(
                extraIdentity.getOutputsList().toArray(new String[0]),
                lastStage.getOutputTypes());
        pipelineBuilder.setOutputs(frame(outputColumns));
    }

    private static Transformation convertColOp(H2OColOp stage){
        Transformation.Builder builder = Transformation.newBuilder();
        setCustomBuilderForColOp(builder, stage.getAst(), stage.name());
        for (String inputColumn : stage.getOldNames()) {
            builder.addInputs(inputColumn);
        }
        for (String outputColumn : stage.getNewNames()) {
            builder.addOutputs(outputColumn);
        }
        return builder.build();
    }
    
    private static void setCustomBuilderForColOp(Transformation.Builder builder, AstExec ast, String stageName) {
        String functionName = ast._asts[0].str();
        Custom.CustomParam functionParam = Custom.CustomParam.newBuilder()
            .setName("function")
            .setStringParam(functionName)
            .build();
        if (MathUnaryTransform.Factory.functionExists(functionName)) {
            builder.setCustomOp(
                Custom.CustomOp.newBuilder()
                    .setTransformerName(MathUnaryTransform.Factory.TRANSFORMER_ID)
                    .addParams(functionParam)
                    .build());
        } else if (MathBinaryTransform.Factory.functionExists(functionName)) {
            builder.setCustomOp(
                Custom.CustomOp.newBuilder()
                    .setTransformerName(MathBinaryTransform.Factory.TRANSFORMER_ID)
                    .addParams(functionParam)
                    .build());
        } else {
            throw new UnsupportedOperationException(
                String.format("The function '%s' in the stage '%s' is not supported.", functionName, stageName));
        }
    }

    private static Frame frame(Column[] cols) {
        return Frame.newBuilder().addAllColumns(Arrays.asList(cols)).build();
    }
}
