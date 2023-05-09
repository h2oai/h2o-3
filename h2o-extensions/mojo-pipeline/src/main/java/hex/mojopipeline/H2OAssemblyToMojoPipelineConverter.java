package hex.mojopipeline;

import hex.genmodel.mojopipeline.transformers.*;
import mojo.spec.Custom;
import mojo.spec.PipelineOuterClass;
import water.fvec.ByteVec;
import water.fvec.NFSFileVec;
import water.parser.ParseTime;
import water.rapids.Assembly;
import water.rapids.ast.AstParameter;
import water.rapids.ast.params.AstId;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstStr;
import water.rapids.ast.params.AstStrList;
import water.rapids.transforms.*;
import mojo.spec.PipelineOuterClass.Pipeline;
import mojo.spec.PipelineOuterClass.Transformation;
import mojo.spec.PipelineOuterClass.Frame;
import mojo.spec.ColumnOuterClass.Column;
import mojo.spec.ColumnOuterClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class H2OAssemblyToMojoPipelineConverter {
    
    public static Pipeline convertToProtoBufPipeline(Assembly assembly) {
        final Transform[] stages = assembly.steps();
        final Transform firstStage = stages[0];
        final Transform lastStage = stages[stages.length - 1];
        
        Pipeline.Builder pipelineBuilder = Pipeline.newBuilder();
        Column[] inputColumns = convertColumns(firstStage.getInputNames(), firstStage.getInputTypes());
        pipelineBuilder.setFeatures(frame(inputColumns));

        Frame.Builder interimsFrameBuilder = Frame.newBuilder();
        
        
        InplaceOperationSimulator inplaceOperationSimulator = new InplaceOperationSimulator();
        for (Transform stage : stages) {
            Transformation transformation = convertStage(stage, inplaceOperationSimulator);
            pipelineBuilder.addTransformations(transformation);
            if (!stage.isInPlace() &&stage.getNewNames().length > 0) {
                Column[] tempColumns = convertColumns(stage.getNewNames(), stage.getNewTypes());
                interimsFrameBuilder.addAllColumns(Arrays.asList(tempColumns));
            }
        }
        Column[] replacementColumns = convertColumns(
                inplaceOperationSimulator.getReplacementColumnNames(), 
                inplaceOperationSimulator.getReplacementColumnTypes());
        interimsFrameBuilder.addAllColumns(Arrays.asList(replacementColumns));
        pipelineBuilder.setInterims(interimsFrameBuilder);
        setOutputColumns(pipelineBuilder, lastStage, inplaceOperationSimulator);
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

    private static Transformation convertStage(Transform stage, InplaceOperationSimulator inplaceOperationSimulator){
        if (stage instanceof H2OColSelect) {
            return convertColSelect((H2OColSelect)stage, inplaceOperationSimulator);
        } else if (stage instanceof H2OBinaryOp) {
            return convertBinaryOp((H2OBinaryOp)stage, inplaceOperationSimulator);
        } else if (stage instanceof H2OColOp) {
            return convertColOp((H2OColOp)stage, inplaceOperationSimulator);
        } else {
            throw new UnsupportedOperationException(
                String.format("Stage conversion of type %s is not supported yet.", stage.getClass().getName()));
        }
    }

    private static Transformation convertColSelect(H2OColSelect stage, InplaceOperationSimulator inplaceOperationSimulator){
        Transformation.Builder builder = Transformation.newBuilder();
        builder.setIdentityOp(PipelineOuterClass.IdentityOp.newBuilder());
        for (String outputColumn : stage.getOutputNames()) {
            String updatedColumn = inplaceOperationSimulator.updateColumn(outputColumn);
            builder.addInputs(updatedColumn);
            builder.addOutputs(updatedColumn);
        }
        return builder.build();
    }

    private static void setOutputColumns(
            Pipeline.Builder pipelineBuilder,
            Transform lastStage,
            InplaceOperationSimulator inplaceOperationSimulator) {
        Transformation.Builder builder = Transformation.newBuilder();
        builder.setIdentityOp(PipelineOuterClass.IdentityOp.newBuilder());
        for (String outputColumn : lastStage.getOutputNames()) {
            String inputColumn = inplaceOperationSimulator.updateColumn(outputColumn);
            builder.addInputs(inputColumn);
            builder.addOutputs("assembly_" + outputColumn);
        }
        Transformation extraIdentity = builder.build();
        pipelineBuilder.addTransformations(extraIdentity);
        Column[] outputColumns = convertColumns(
                extraIdentity.getOutputsList().toArray(new String[0]),
                lastStage.getOutputTypes());
        pipelineBuilder.setOutputs(frame(outputColumns));
    }

    private static Transformation convertColOp(H2OColOp stage, InplaceOperationSimulator inplaceOperationSimulator){
        Transformation.Builder builder = Transformation.newBuilder();
        String functionName = stage.getAst()._asts[0].str();
        Custom.CustomParam functionParam = Custom.CustomParam.newBuilder()
                .setName("function")
                .setStringParam(functionName)
                .build();
        Custom.CustomParam timezoneParam = Custom.CustomParam.newBuilder()
                .setName("timezone")
                .setStringParam(ParseTime.getTimezone().getID())
                .build();
        Custom.CustomOp.Builder customOpBuilder = Custom.CustomOp.newBuilder();
        customOpBuilder.addParams(functionParam);
        customOpBuilder.addParams(timezoneParam);
        convertParameters(stage, customOpBuilder);
        
        if (MathUnaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(MathUnaryTransform.Factory.TRANSFORMER_ID);
        } else if (StringUnaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(StringUnaryTransform.Factory.TRANSFORMER_ID);
        } else if (StringPropertiesUnaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(StringPropertiesUnaryTransform.Factory.TRANSFORMER_ID);
        } else if (StringGrepTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(StringGrepTransform.Factory.TRANSFORMER_ID);
        } else if (StringSplitTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(StringSplitTransform.Factory.TRANSFORMER_ID);
        } else if (TimeUnaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(TimeUnaryTransform.Factory.TRANSFORMER_ID);
        } else if (ToStringConversion.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(ToStringConversion.Factory.TRANSFORMER_ID);
        } else if (ToNumericConversion.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(ToNumericConversion.Factory.TRANSFORMER_ID);
        } else {
            throw new UnsupportedOperationException(
                    String.format("The function '%s' in the stage '%s' is not supported.", functionName, stage.name()));
        }
        builder.setCustomOp(customOpBuilder.build());
        for (String inputColumn : stage.getOldNames()) {
            String updatedColumn = inplaceOperationSimulator.updateColumn(inputColumn);
            builder.addInputs(updatedColumn);
        }
        if (stage.isInPlace()) {
            String[] oldNames =  stage.getOldNames();
            for (int i = 0; i < oldNames.length; i++) {
                String oldName = oldNames[i];
                String newName = "temp_" + oldName + "_" + stage.name();
                inplaceOperationSimulator.setNewReplacement(oldName, newName, stage.getNewTypes()[i]);
                builder.addOutputs(newName);
            }
        } else {
            for (String outputColumn : stage.getNewNames()) {
                builder.addOutputs(outputColumn);
            }
        }
        return builder.build();
    }
    
    private static void convertParameters(H2OColOp stage, Custom.CustomOp.Builder builder) {
        for (Map.Entry<String,AstParameter> entry: stage.getParams().entrySet()) {
            String name = entry.getKey();
            AstParameter value = entry.getValue();
            Custom.CustomParam.Builder paramBuilder = Custom.CustomParam.newBuilder().setName(name);
            if (value instanceof AstNum) {
                AstNum parameter = (AstNum) value;
                paramBuilder.setFloat64Param(parameter.getNum());
            } else if (value instanceof AstStr) {
                AstStr parameter = (AstStr) value;
                paramBuilder.setStringParam(parameter.getStr());
            } else if (value instanceof AstStrList) {
                AstStrList parameter = (AstStrList) value;
                String joined = String.join("`````", parameter._strs);
                paramBuilder.setStringParam(joined);
            } else if (value instanceof AstId) {
                AstId parameter = (AstId) value;
                paramBuilder.setStringParam(parameter.str());
            }
            builder.addParams(paramBuilder.build());
        }
    }

    private static Transformation convertBinaryOp(H2OBinaryOp stage, InplaceOperationSimulator inplaceOperationSimulator){
        Transformation.Builder builder = Transformation.newBuilder();
        String functionName = stage.getAst()._asts[0].str();
        Custom.CustomOp.Builder customOpBuilder = Custom.CustomOp.newBuilder();
        customOpBuilder.addParams(
                Custom.CustomParam.newBuilder()
                        .setName("function")
                        .setStringParam(functionName)
                        .build());
        customOpBuilder.addParams(
                Custom.CustomParam.newBuilder()
                        .setName("isLeftCol")
                        .setBoolParam(stage.getIsLeftColumn())
                        .build());
        customOpBuilder.addParams(
                Custom.CustomParam.newBuilder()
                        .setName("isRightCol")
                        .setBoolParam(stage.getIsRightColumn())
                        .build());
        if(!stage.getIsLeftColumn()) {
            customOpBuilder.addParams(
                    Custom.CustomParam.newBuilder()
                            .setName("constValue")
                            .setFloat64Param(stage.getAst()._asts[1].exec(null).getNum())
                            .build());
        }
        if(!stage.getIsRightColumn()) {
            customOpBuilder.addParams(
                    Custom.CustomParam.newBuilder()
                            .setName("constValue")
                            .setFloat64Param(stage.getAst()._asts[2].exec(null).getNum())
                            .build());
        }
        convertParameters(stage, customOpBuilder);
        if (MathBinaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(MathBinaryTransform.Factory.TRANSFORMER_ID);
        } else if (StringPropertiesBinaryTransform.Factory.functionExists(functionName)) {
            customOpBuilder.setTransformerName(StringPropertiesBinaryTransform.Factory.TRANSFORMER_ID);
        } else {
            throw new UnsupportedOperationException(
                    String.format("The function '%s' in the stage '%s' is not supported.", functionName, stage.name()));
        }
        builder.setCustomOp(customOpBuilder.build());
        for (String inputColumn : stage.getOldNames()) {
            String updatedColumn = inplaceOperationSimulator.updateColumn(inputColumn);
            builder.addInputs(updatedColumn);
        }
        if (stage.isInPlace()) {
            String[] oldNames =  stage.getOldNames();
            String oldName = oldNames[0];
            String newName = "temp_" + oldName + "_" + stage.name();
            inplaceOperationSimulator.setNewReplacement(oldName, newName, stage.getNewTypes()[0]);
            builder.addOutputs(newName);
        } else {
            for (String outputColumn : stage.getNewNames()) {
                builder.addOutputs(outputColumn);
            }
        }
        return builder.build();
    }

    private static Frame frame(Column[] cols) {
        return Frame.newBuilder().addAllColumns(Arrays.asList(cols)).build();
    }
}
