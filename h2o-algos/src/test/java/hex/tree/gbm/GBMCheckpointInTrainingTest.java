package hex.tree.gbm;

import hex.Model;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.tree.GlobalInteractionConstraints;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.IcedHashSet;
import water.util.IcedInt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class GBMCheckpointInTrainingTest extends TestUtil {
    
    @Rule
    public transient TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testPartialCheckpointGivesTheSameResultAsTheFinalModel() throws IOException {
        Scope.enter();
        try {
            // prepare training data
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            // Common model parameters
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;

            // Train referential model
            parms._ntrees = 3;
            GBMModel modelReference = Scope.track_generic(new GBM(parms).trainModel().get());
            Frame scoreReference = modelReference.score(train);
            Scope.track(scoreReference);

            // Train another model and do in-training checkpoints
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder("gbm_checkpoints").getAbsolutePath();
            parms._ntrees = 6;
            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            assertNotNull(gbm);

            // Load in-training checkpoint with the same trees as reference has
            Model checkpoint = Model.importBinaryModel(parms._in_training_checkpoints_dir + "/gbm.ntrees_3");
            Scope.track_generic(checkpoint);
            Frame scoreCheckpoint = checkpoint.score(train);
            Scope.track(scoreCheckpoint);

            System.out.println("modelReference = " + modelReference);
            System.out.println("checkpoint = " + checkpoint);

            // Given output should be the same
            assertFrameEquals(scoreReference, scoreCheckpoint, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPartialCheckpointAreProperlyExported() throws IOException {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 4;
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());

            File checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPartialCheckpointAreProperlyExported_definedInterval() throws IOException {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 10;
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._in_training_checkpoints_tree_interval = 3;

            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm_checkpoint_interval")).trainModel().get());

            File checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid % parms._in_training_checkpoints_tree_interval != 0) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPartialCheckpointAreProperlyExported_restart() throws IOException {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 4;
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());

            File checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints for 1, 2, 3 trees
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }

            // Load the last checkpoint and restart training
            Model checkpoint = Model.importBinaryModel(parms._in_training_checkpoints_dir + "/gbm.ntrees_3");
            Scope.track_generic(checkpoint);

            parms._ntrees = 5;

            // Export checkpoint to the different folder
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._checkpoint = checkpoint._key;
            GBMModel gbmRestart = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            assertNotNull(gbmRestart);

            checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints only for 4. tree and nothing else
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid < 4) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPartialCheckpointAreProperlyExported_restartWithDefinedInterval() throws IOException {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 10;
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._in_training_checkpoints_tree_interval = 2;

            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());

            File checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints for 2, 4, 6, 8
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid % parms._in_training_checkpoints_tree_interval != 0) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }

            int chooseCheckpointNumber = 4;
            Model checkpoint = Model.importBinaryModel(parms._in_training_checkpoints_dir + "/gbm.ntrees_" + chooseCheckpointNumber);
            Scope.track_generic(checkpoint);

            // Export checkpoints to the different folder
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._checkpoint = checkpoint._key;
            GBMModel gbmRestart = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            assertNotNull(gbmRestart);

            checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints only for 6, 8
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid <= chooseCheckpointNumber) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                if ((tid - chooseCheckpointNumber) % parms._in_training_checkpoints_tree_interval != 0) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPartialCheckpointAreProperlyExported_restartAndChangeInterval() throws IOException {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 10;
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._in_training_checkpoints_tree_interval = 3;

            GBMModel gbm = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());

            File checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints for 3, 6, 9
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid % parms._in_training_checkpoints_tree_interval != 0) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }

            int chooseCheckpointNumber = 3;
            Model checkpoint = Model.importBinaryModel(parms._in_training_checkpoints_dir + "/gbm.ntrees_" + chooseCheckpointNumber);
            Scope.track_generic(checkpoint);
            Frame scoreCheckpoint = checkpoint.score(train);
            Scope.track(scoreCheckpoint);

            // Export checkpoints to the different folder
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();
            parms._checkpoint = checkpoint._key;

            // Change the interval of checkpoints
            parms._in_training_checkpoints_tree_interval = 2;
            GBMModel gbmRestart = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            assertNotNull(gbmRestart);

            checkpointsDirectory = new File(parms._in_training_checkpoints_dir);
            assertTrue(checkpointsDirectory.exists());
            assertTrue(checkpointsDirectory.isDirectory());

            // There should be checkpoints for 5, 7, 9
            for (int tid = 1; tid < parms._ntrees; tid++) {
                File checkpointFile = new File(parms._in_training_checkpoints_dir, gbm._key.toString() + ".ntrees_" + tid);
                if (tid <= chooseCheckpointNumber) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                if ((tid - chooseCheckpointNumber) % parms._in_training_checkpoints_tree_interval != 0) {
                    assertFalse(checkpointFile.exists());
                    continue;
                }
                assertTrue(checkpointFile.exists());
                assertTrue(checkpointFile.isFile());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testUsageOfPartialCheckpointGivesTheSameModelPrediction() throws IOException {
        Scope.enter();
        try {
            // prepare training data
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            // Common model parameters
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;

            // Train referential model
            parms._ntrees = 6;
            GBMModel modelReference = Scope.track_generic(new GBM(parms).trainModel().get());
            Frame scoreReference = modelReference.score(train);
            Scope.track(scoreReference);

            // Train another model and do in-training checkpoints
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder("gbm_checkpoints").getAbsolutePath();
            parms._ntrees = 4;
            GBMModel gbmWithCheckpoints = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            assertNotNull(gbmWithCheckpoints);

            // Load in-training checkpoint with 2. trees and use it as checkpoint for another training
            Model checkpoint = Model.importBinaryModel(parms._in_training_checkpoints_dir + "/gbm.ntrees_2");
            Scope.track_generic(checkpoint);

            // Train another model and do in-training checkpoints
            parms._ntrees = 6;
            parms._checkpoint = checkpoint._key;
            parms._in_training_checkpoints_dir = null;
            GBMModel gbmFinal = Scope.track_generic(new GBM(parms).trainModel().get());
            Frame scoreFinal = gbmFinal.score(train);
            Scope.track(scoreFinal);

            // Given output must be the same
            assertFrameEquals(scoreReference, scoreFinal, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCheckpointingDoesNotChangeModel() throws IOException {
        Scope.enter();
        try {
            // prepare training data
            String response = "CAPSULE";
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            train.toCategoricalCol(response);

            // Common model parameters
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 0xDEDA;
            parms._train = train._key;
            parms._response_column = response;
            parms._ntrees = 6;

            // Train referential model
            GBMModel modelReference = Scope.track_generic(new GBM(parms).trainModel().get());
            Frame scoreReference = modelReference.score(train);
            Scope.track(scoreReference);

            // Train another model and do in-training checkpoints
            parms._in_training_checkpoints_dir = temporaryFolder.newFolder("gbm_checkpoints").getAbsolutePath();
            GBMModel gbmWithCheckpoints = Scope.track_generic(new GBM(parms, Key.make("gbm")).trainModel().get());
            Frame scoreGBMWithCheckpoints = gbmWithCheckpoints.score(train);
            Scope.track(scoreGBMWithCheckpoints);

            // Given output must be the same
            assertFrameEquals(scoreReference, scoreGBMWithCheckpoints, 1e-3);
        } finally {
            Scope.exit();
        }
    }
}
