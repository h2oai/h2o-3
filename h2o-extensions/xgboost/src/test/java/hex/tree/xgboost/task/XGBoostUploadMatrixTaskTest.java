package hex.tree.xgboost.task;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import hex.tree.xgboost.matrix.RemoteMatrixLoader;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class XGBoostUploadMatrixTaskTest {

    @Test
    public void dense() {
        Assume.assumeTrue(H2O.CLOUD.isSingleNode());
        Scope.enter();
        try {
            Frame df = parseTestFile("bigdata/laptop/higgs_head_2M.csv");
            Scope.track(df);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = df._key;
            parms._response_column = df.name(0);
            XGBoost xgboost = new hex.tree.xgboost.XGBoost(parms);
            XGBoostModel model = new XGBoostModel(Key.make(), parms, new XGBoostOutput(xgboost), df, null);
            model._output._sparse = false;
            Scope.track_generic(model.model_info().dataInfo());
            new XGBoostUploadMatrixTask(
                model, df, true, new boolean[] { true },
                new String[] {H2O.getIpPortString()}, H2O.ARGS.jks != null, null, null, null
            ).run();
            MatrixLoader.DMatrixProvider remoteProvider = new RemoteMatrixLoader(model._key).makeLocalTrainMatrix();
            MatrixLoader.DMatrixProvider localProvider = new FrameMatrixLoader(model, df, null).makeLocalTrainMatrix();

            System.out.println("------ LOCAL -----");
            localProvider.print(10);
            System.out.println("------ REMOTE ------");
            remoteProvider.print(10);
            assertEquals(localProvider, remoteProvider);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void sparse() {
        Assume.assumeTrue(H2O.CLOUD.isSingleNode());
        Scope.enter();
        try {
            Frame df = parseTestFile("bigdata/laptop/higgs_head_2M.csv");
            Scope.track(df);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = df._key;
            parms._response_column = df.name(0);
            XGBoost xgboost = new hex.tree.xgboost.XGBoost(parms);
            XGBoostModel model = new XGBoostModel(Key.make(), parms, new XGBoostOutput(xgboost), df, null);
            model._output._sparse = true;
            Scope.track_generic(model.model_info().dataInfo());
            new XGBoostUploadMatrixTask(
                model, df, true, new boolean[] { true },
                new String[] {H2O.getIpPortString()}, H2O.ARGS.jks != null, null, null, null
            ).run();
            MatrixLoader.DMatrixProvider remoteProvider = new RemoteMatrixLoader(model._key).makeLocalTrainMatrix();
            MatrixLoader.DMatrixProvider localProvider = new FrameMatrixLoader(model, df, null).makeLocalTrainMatrix();

            System.out.println("------ LOCAL -----");
            localProvider.print(10);
            System.out.println("------ REMOTE ------");
            remoteProvider.print(10);
            assertEquals(localProvider, remoteProvider);
        } finally {
            Scope.exit();
        }
    }

}
