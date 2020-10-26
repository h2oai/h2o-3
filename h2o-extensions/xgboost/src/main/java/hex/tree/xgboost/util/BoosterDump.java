package hex.tree.xgboost.util;

import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import ai.h2o.xgboost4j.java.Booster;
import ai.h2o.xgboost4j.java.XGBoostError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

public class BoosterDump {

    public static String[] getBoosterDump(byte[] boosterBytes, String featureMap, final boolean withStats, final String format) {
        final Path featureMapFile;
        if (featureMap != null && ! featureMap.isEmpty())
            try {
                featureMapFile = Files.createTempFile("featureMap", ".txt");
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write a temporary file with featureMap");
            }
        else
            featureMapFile = null;
        try {
            if (featureMapFile != null) {
                Files.write(featureMapFile, Collections.singletonList(featureMap), Charset.defaultCharset(), StandardOpenOption.WRITE);
            }
            Booster booster = BoosterHelper.loadModel(new ByteArrayInputStream(boosterBytes));
            BoosterHelper.BoosterOp<String[]> dumpOp = booster1 -> {
                String featureMap1 = featureMapFile != null ? featureMapFile.toFile().getAbsolutePath() : null;
                return booster1.getModelDump(featureMap1, withStats, format);
            };
            return BoosterHelper.doWithLocalRabit(dumpOp, booster);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write feature map file", e);
        } catch (XGBoostError e) {
            throw new IllegalStateException("Failed to dump model", e);
        } finally {
            if (featureMapFile != null) {
                try {
                    Files.deleteIfExists(featureMapFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || ! "--dump".equals(args[0])) {
            usage();
            System.exit(1);
        }
        String mojoFile = args[1];
        boolean withStats = args.length > 2 && Boolean.parseBoolean(args[2]);
        String format = args.length > 3 ? args[3] : "text";
        String featureMap = null;
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoFile);
        if (reader.exists("feature_map")) {
            featureMap = new String(reader.getBinaryFile("feature_map"), StandardCharsets.UTF_8);
        }
        byte[] boosterBytes = reader.getBinaryFile("boosterBytes");
        for (String dumpLine : getBoosterDump(boosterBytes, featureMap, withStats, format)) {
            System.out.println(dumpLine);
        }
    }

    private static void usage() {
        System.out.println("java -cp h2o-genmodel.jar " + BoosterDump.class.getCanonicalName() + " --dump <mojo> [withStats?] [format]");
    }

}
