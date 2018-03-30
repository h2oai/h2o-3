package hex.schemas;

import hex.coxph.CoxPHModel;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.util.TwoDimTable;

public class CoxPHModelV3 extends ModelSchemaV3<CoxPHModel,
                                              CoxPHModelV3,
                                              CoxPHModel.CoxPHParameters,
                                              CoxPHV3.CoxPHParametersV3,
                                              CoxPHModel.CoxPHOutput,
                                              CoxPHModelV3.CoxPHModelOutputV3> {

  public static final class CoxPHModelOutputV3 extends ModelOutputSchemaV3<CoxPHModel.CoxPHOutput, CoxPHModelOutputV3> {

    @API(help="Table of Coefficients")
    TwoDimTableV3 coefficients_table;

    double[][] var_coef;
    @API(help = "null_loglik")
    double null_loglik;
    @API(help = "loglik")
    double loglik;
    @API(help = "loglik_test")
    double loglik_test;
    @API(help = "wald_test")
    double wald_test;
    @API(help = "score_test")
    double score_test;
    @API(help = "rsq")
    double rsq;
    @API(help = "maxrsq")
    double maxrsq;
    @API(help = "lre")
    double lre;
    @API(help = "iter")
    int iter;
    @API(help = "offset_names")
    String[] offset_names;
    @API(help = "n")
    long n;
    @API(help = "n_missing")
    long n_missing;
    @API(help = "total_event")
    long total_event;
    @API(help = "time")
    double[] time;
    @API(help = "n_risk")
    double[] n_risk;
    @API(help = "n_event")
    double[] n_event;
    @API(help = "n_censor")
    double[] n_censor;
    @API(help = "cumhaz_0")
    double[] cumhaz_0;
    @API(help = "var_cumhaz_1")
    double[] var_cumhaz_1;
    @API(help = "var_cumhaz_2")
    double[][] var_cumhaz_2;
    @API(help = "formula")
    String formula;
    @API(help = "ties", values = {"efron", "breslow"})
    CoxPHModel.CoxPHParameters.CoxPHTies ties;

    @Override
    public CoxPHModelOutputV3 fillFromImpl(CoxPHModel.CoxPHOutput impl) {
      super.fillFromImpl(impl);
      String[] names = impl._coef_names;
      String[] colTypes = new String[]{"double", "double", "double", "double", "double"};
      String[] colFormats = new String[]{"%5f", "%5f", "%5f", "%5f", "%5f"};
      String[] colNames = new String[]{"Coefficients", "exp_coef", "exp_neg_coef", "se_coef", "z_coef"};
      TwoDimTable tdt = new TwoDimTable("Coefficients","CoxPH Coefficients", names, colNames, colTypes, colFormats, "names");
      // fill in coefficients
      for (int i = 0; i < names.length; i++) {
        tdt.set(i, 0, impl._coef[i]);
        tdt.set(i, 1, impl._exp_coef[i]);
        tdt.set(i, 2, impl._exp_neg_coef[i]);
        tdt.set(i, 3, impl._se_coef[i]);
        tdt.set(i, 4, impl._z_coef[i]);
      }
      coefficients_table = new TwoDimTableV3().fillFromImpl(tdt);
      return this;
    }
  } // CoxPHModelOutputV3

  public CoxPHV3.CoxPHParametersV3 createParametersSchema() { return new CoxPHV3.CoxPHParametersV3(); }
  public CoxPHModelOutputV3 createOutputSchema() { return new CoxPHModelOutputV3(); }

}
