package water.rapids.ast.prims.models;

import hex.Model;
import hex.ModelContainer;
import hex.leaderboard.*;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.Log;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Compute Leaderboard
 */
public class AstMakeLeaderboard extends AstPrimitive {

    @Override
    public String[] args() {
        return new String[]{"models", "leaderboardFrame", "sortMetric", "extensions", "scoringData"};
    }

    @Override
    public int nargs() {
        return 1 + 5;
    } // (makeLeaderboard models leaderboardFrame sortMetric extensions projectName)

    @Override
    public String str() {
        return "makeLeaderboard";
    }

    private static LeaderboardExtensionsProvider createLeaderboardExtensionProvider(Frame leaderboardFrame) {

        return new LeaderboardExtensionsProvider() {
            @Override
            public LeaderboardCell[] createExtensions(Model model) {
                return new LeaderboardCell[]{
                        new TrainingTime(model),
                        new ScoringTimePerRow(model, leaderboardFrame),
                        new AlgoName(model),
                };
            }
        };
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Key[] models = Arrays.stream(stk.track(asts[1].exec(env)).getStrs())
                .flatMap(model_id -> {
                    Object obj = DKV.getGet(model_id);
                    if (obj instanceof Model) {
                        return Stream.of(Key.make(model_id));
                    } else if (obj instanceof ModelContainer) {
                        ModelContainer mc = (ModelContainer) obj;
                        return Stream.of(mc.getModelKeys());
                    } else {
                        throw new RuntimeException("Unsupported model/grid id: " + model_id + "!");
                    }
                }).toArray(Key[]::new);

        // Get Frame clones Frame and the clone is key-less and since we are doing just read only ops here we can use the original frame
        String leaderboardFrameKey = stk.track(asts[2].exec(env)).getStr();
        Frame leaderboardFrame = null;
        if (!leaderboardFrameKey.isEmpty())
            leaderboardFrame = DKV.getGet(leaderboardFrameKey);
        else
            leaderboardFrameKey = null;
        String sortMetric = stk.track(asts[3].exec(env)).getStr();
        String[] extensions = stk.track(asts[4].exec(env)).getStrs();
        String scoringData = stk.track(asts[5].exec(env)).getStr().toLowerCase();

        String projectName = leaderboardFrameKey + "_" + Arrays.deepHashCode(models) + "_" + scoringData;

        Arrays.stream(models).forEach(DKV::prefetch);

        if (sortMetric.equalsIgnoreCase("auto")) sortMetric = null;

        final boolean oneTrainingFrame = Arrays.stream(models).map(m -> ((Model) DKV.getGet(m))._parms._train).distinct().count() == 1;
        final boolean oneValidationFrame = Arrays.stream(models).map(m -> ((Model) DKV.getGet(m))._parms._valid).distinct().count() == 1;
        final boolean oneNFoldsSetting = Arrays.stream(models)
                .map(m -> ((Model) DKV.getGet(m))._parms)
                .filter(parms -> !parms.algoName().equalsIgnoreCase("stackedensemble"))
                .map(parameters -> parameters._nfolds)
                .distinct()
                .count() == 1;
        final boolean allCV = Arrays.stream(models).allMatch(m -> ((Model) DKV.getGet(m))._output._cross_validation_metrics != null);
        final boolean allHasValid = Arrays.stream(models).allMatch(m -> ((Model) DKV.getGet(m))._output._validation_metrics != null);

        boolean warnAboutTrain = false;
        boolean warnAboutValid = false;
        boolean warnAboutNFolds = false;
        boolean warnAboutLeaderboard = false;

        if (scoringData.equals("auto") && leaderboardFrame == null) {
            warnAboutTrain = true;
            warnAboutNFolds = true;
            scoringData = "xval";
        }
        if (scoringData.equals("xval")) {
            warnAboutTrain = true;
            warnAboutNFolds = true;
            warnAboutLeaderboard = true;
            if (!allCV)
                scoringData = "valid";
        }
        if (scoringData.equals("valid")) {
            warnAboutTrain = false;
            warnAboutValid = true;
            warnAboutLeaderboard = true;
            if (!allHasValid)
                scoringData = "train";
        }
        if (scoringData.equals("train")) {
            warnAboutTrain = true;
            warnAboutValid = false;
            warnAboutLeaderboard = true;
        }

        // One training frame can be false positive if models from two different automls are used.
        if (warnAboutTrain && !oneTrainingFrame)
            Log.warn("More than one training frame was used amongst the models provided to the leaderboard.");
        if (warnAboutValid && !oneValidationFrame)
            Log.warn("More than one validation frame was used amongst the models provided to the leaderboard.");
        if (warnAboutNFolds && !oneNFoldsSetting)
            Log.warn("More than one n-folds settings are present."); //had to exclude SEs
        if (warnAboutLeaderboard && leaderboardFrame != null)
            Log.warn("Leaderboard frame present but scoring data are set to " + scoringData +
                    ". Using scores from " + scoringData + ".");

        final Logger logger = LoggerFactory.getLogger(Leaderboard.class);
        Leaderboard ldb = Leaderboard.getOrMake(projectName, logger, leaderboardFrame, sortMetric, Leaderboard.ScoreData.valueOf(scoringData));
        ldb.setExtensionsProvider(createLeaderboardExtensionProvider(leaderboardFrame));
        ldb.addModels(models);
        ldb.ensureSorted();
        Frame leaderboard = ldb.toTwoDimTable(extensions).asFrame(Key.make());
        return new ValFrame(leaderboard);
    }
}
