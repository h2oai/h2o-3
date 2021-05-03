package hex.glm;

import hex.StringPair;
import org.junit.Assert;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

public class GLMMojoInteractionTestGaussian extends TestUtil {
    @Test
    public void testGLMMojoInteractionGaussian() {
        Frame fr2 = new TestFrameBuilder()
                .withColNames("response", "N1", "N2", "N3", "C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ard(1,0,1,0,1,0,1,0,1,0))
                .withDataForCol(1, ard(3,1,1,3,1,1,2,3,3,1))
                .withDataForCol(2, ard(3,2,2,1,2,1,1,2,1,2))
                .withDataForCol(3, ard(2,2,1,2,2,2,3,2,1,1))
                .withDataForCol(4, ar('a','a','a','b','a','b','a','b','b','a'))
                .withDataForCol(5, ar('A', 'B', 'B', 'A', 'A', 'A', 'B', 'B', 'A', 'A'))
                .withDataForCol(6, ar("foo", "bar", "foo", "bar", "foo", "foo", "bar", "foo", "bar","bar"))
                .build();
        Frame fr = fr2.deepCopy(Key.make().toString());

        GLMModel.GLMParameters params = new GLMModel.GLMParameters();
        params._response_column = "z";
        params._family = GLMModel.GLMParameters.Family.gaussian;
        params._standardize = false;
        params._train = fr._key;
        params._ignore_const_cols = false;
        params._intercept = false;
        params._seed = 42;

        params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;
        params._interaction_pairs = new StringPair[]{
                new StringPair("N1", "N2"),
                new StringPair("N2", "N3"),
                new StringPair("N1", "N3"),
                new StringPair("N1", "C1"),
                new StringPair("N2", "C2"),
                new StringPair("N3", "C3"),
                new StringPair("C1", "C2"),
                new StringPair("C2", "C3"),
                new StringPair("C1", "C3"),};

        // just check it doesn't crash
        GLMModel model = new GLM(params).trainModel().get();
        Scope.track_generic(model);

        Frame pred = model.score(fr2);
        Scope.track(pred);
        Assert.assertTrue(model.testJavaScoring(fr2, pred, 1e-10));
    }
}
