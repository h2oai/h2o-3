package hex.mojo;

import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.junit.Before;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.io.*;
import java.util.ArrayList;

import static hex.genmodel.utils.DistributionFamily.AUTO;
import static org.junit.Assert.*;

public class GenericModelTest extends TestUtil {

    @Before
    public void setUp() {
        TestUtil.stall_till_cloudsize(1);
    }
    
    /**
     * Create a GBM model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     * 
     */
    @Test
    public void downloadable_mojo_gbm() throws IOException {
        GBMModel gbm = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new GBM model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            gbm.getMojo().writeTo(originalModelMojo);
            gbm.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());
            
            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._mojo_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            generic.init(false);
            genericModel = generic.trainModel().get();
            
            // Compare the two MOJOs byte-wise
            ByteArrayOutputStream genericModelMojo = new ByteArrayOutputStream();
            genericModel.getMojo().writeTo(genericModelMojo);
            assertArrayEquals(genericModelMojo.toByteArray(), genericModelMojo.toByteArray());
            
        } finally {
            if(gbm != null) gbm.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    /**
     * Create a DRF model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_drf() throws IOException {
        DRFModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new DRF model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            originalModel = job.trainModel().get();
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(originalModelMojo);
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._mojo_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            generic.init(false);
            genericModel = generic.trainModel().get();

            // Compare the two MOJOs byte-wise
            ByteArrayOutputStream genericModelMojo = new ByteArrayOutputStream();
            genericModel.getMojo().writeTo(genericModelMojo);
            assertArrayEquals(genericModelMojo.toByteArray(), genericModelMojo.toByteArray());

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    /**
     * Create an IRF model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_irf() throws IOException {
        IsolationForestModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new IRF model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            originalModel = job.trainModel().get();
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(originalModelMojo);
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._mojo_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            generic.init(false);
            genericModel = generic.trainModel().get();

            // Compare the two MOJOs byte-wise
            ByteArrayOutputStream genericModelMojo = new ByteArrayOutputStream();
            genericModel.getMojo().writeTo(genericModelMojo);
            assertArrayEquals(genericModelMojo.toByteArray(), genericModelMojo.toByteArray());

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    /**
     * Create a GLM model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_glm() throws IOException {
        GLMModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new DRF model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];

            GLM job = new GLM(parms);
            originalModel = job.trainModel().get();
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(originalModelMojo);
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._mojo_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            generic.init(false);
            genericModel = generic.trainModel().get();

            // Compare the two MOJOs byte-wise
            ByteArrayOutputStream genericModelMojo = new ByteArrayOutputStream();
            genericModel.getMojo().writeTo(genericModelMojo);
            assertArrayEquals(genericModelMojo.toByteArray(), genericModelMojo.toByteArray());

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    private Key<Frame> importMojo(final String mojoAbsolutePath) {
        final ArrayList<String> keys = new ArrayList<>(1);
        H2O.getPM().importFiles(mojoAbsolutePath, "", new ArrayList<String>(), keys, new ArrayList<String>(),
                new ArrayList<String>());
        assertEquals(1, keys.size());
        return DKV.get(keys.get(0))._key;
    }

}
