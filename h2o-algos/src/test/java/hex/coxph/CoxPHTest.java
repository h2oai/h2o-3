package hex.coxph;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class CoxPHTest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testCoxPHEfron1Var() {
    CoxPHModel model = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/coxph_test/heart.csv");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;

      CoxPH builder = new CoxPH(parms);
      model = builder.trainModel().get();

      assertEquals(model._output._coef[0],        0.0307077486571334,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203471477951459, 1e-8);
      assertEquals(model._output._null_loglik,    -298.121355672984,    1e-8);
      assertEquals(model._output._loglik,         -295.536762216228,    1e-8);
      assertEquals(model._output._score_test,     4.64097294749287,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.6343882547245,      1e-8);
    } finally {
      if (fr != null)
        fr.delete();
      if (model != null)
        model.delete();
    }
  }

  @Test
  public void testCoxPHBreslow1Var()  {
    CoxPHModel model = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/coxph_test/heart.csv");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.breslow;

      CoxPH builder = new CoxPH(parms);
      model = builder.trainModel().get();

      assertEquals(model._output._coef[0],        0.0306910411003801,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203592486905101, 1e-8);
      assertEquals(model._output._null_loglik,    -298.325606736463,    1e-8);
      assertEquals(model._output._loglik,         -295.745227177782,    1e-8);
      assertEquals(model._output._score_test,     4.63317821557301,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.62659510743282,     1e-8);
    } finally {
      if (fr != null)
        fr.delete();
      if (model != null)
        model.delete();
    }
  }

  @Test
  public void testCoxPHEfron1VarNoStart() {
    CoxPHModel model = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/coxph_test/heart.csv");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train           = fr._key;
      parms._start_column    = null;
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant", "start"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;

      CoxPH builder = new CoxPH(parms);
      model = builder.trainModel().get();

      assertEquals(model._output._coef[0],        0.0289468187293998,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000210975113029285, 1e-8);
      assertEquals(model._output._null_loglik,    -314.148170059513,    1e-8);
      assertEquals(model._output._loglik,         -311.946958322919,    1e-8);
      assertEquals(model._output._score_test,     3.97716015008595,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      3.97164529276219,     1e-8);
    } finally {
      if (fr != null)
        fr.delete();
      if (model != null)
        model.delete();
    }
  }

  @Test
  public void testCoxPHBreslow1VarNoStart() {
    CoxPHModel model = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/coxph_test/heart.csv");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train           = fr._key;
      parms._start_column    = null;
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant", "start"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.breslow;

      CoxPH builder = new CoxPH(parms);
      model = builder.trainModel().get();

      assertEquals(model._output._coef[0],        0.0289484855901731,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000211028794751156, 1e-8);
      assertEquals(model._output._null_loglik,    -314.296493366900,    1e-8);
      assertEquals(model._output._loglik,         -312.095342077591,    1e-8);
      assertEquals(model._output._score_test,     3.97665282498882,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      3.97109228128153,     1e-8);
    } finally {
      if (fr != null)
        fr.delete();
      if (model != null)
        model.delete();
    }
  }

}