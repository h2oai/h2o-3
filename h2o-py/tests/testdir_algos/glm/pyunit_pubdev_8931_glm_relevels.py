from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import pandas as pd
from sklearn.datasets import fetch_mldata

# test taken from Ben Epstein.  Thank you.
# PUBDEV-8197: ordinal prediction returns the wrong class even though other classes have higher probability.
def load_mtpl2(n_samples=100000):
    """
    Fetch the French Motor Third-Party Liability Claims dataset.
    https://scikit-learn.org/stable/auto_examples/linear_model/plot_tweedie_regression_insurance_claims.html

    Parameters
    ----------
    n_samples: int, default=100000
    number of samples to select (for faster run time). Full dataset has
    678013 samples.
    """
    # freMTPL2freq dataset from https://www.openml.org/d/41214
    df_freq_h2o = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/freMTPL2freq.arff"))
    df_freq = df_freq_h2o.as_data_frame()
    #df_freq = fetch_mldata(data_id=41214, as_frame=True)["data"]
    df_freq["IDpol"] = df_freq["IDpol"].astype(int)
    df_freq.set_index("IDpol", inplace=True)

    # freMTPL2sev dataset from https://www.openml.org/d/41215
    df_sev_h2o = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/freMTPL2sev.arff"))
    df_sev = df_sev_h2o.as_data_frame()
    # df_sev = fetch_mldata(data_id=41215, as_frame=True)["data"]
    # sum ClaimAmount over identical IDs
    df_sev = df_sev.groupby("IDpol").sum()
    df = df_freq.join(df_sev, how="left")
    df["ClaimAmount"].fillna(0, inplace=True)

    # unquote string fields
    for column_name in df.columns[df.dtypes.values == object]:
        df[column_name] = df[column_name].str.strip("'")
    return df.iloc[:n_samples]


def test_ordinal():
    df = load_mtpl2()
    df.loc[(df["ClaimAmount"] == 0) & (df["ClaimNb"] >= 1), "ClaimNb"] = 0
    df["Exposure"] = df["Exposure"].clip(upper=1)
    df["ClaimAmount"] = df["ClaimAmount"].clip(upper=100000)
    df["PurePremium"] = df["ClaimAmount"] / df["Exposure"]

    X_freq = h2o.H2OFrame(df)
    X_freq["VehBrand"] = X_freq["VehBrand"].asfactor()
    X_freq["VehBrand"] = X_freq["VehBrand"].relevel_by_frequency()

    X_relevel = h2o.H2OFrame(df)
    X_relevel["VehBrand"] = X_relevel["VehBrand"].asfactor()
    X_relevel["VehBrand"] = X_relevel["VehBrand"].relevel("B1") # most frequent category

    response_col = "PurePremium"
    weight_col = "Exposure"
    predictors = "VehBrand"

    glm_freq = H2OGeneralizedLinearEstimator(family="tweedie", solver='IRLSM',tweedie_variance_power=1.5,
                                             tweedie_link_power=0, lambda_=0, compute_p_values=True, 
                                             remove_collinear_columns=True, seed=1)
    glm_relevel = H2OGeneralizedLinearEstimator(family="tweedie", solver='IRLSM', tweedie_variance_power=1.5,
                                                tweedie_link_power=0, lambda_=0,compute_p_values=True, 
                                                remove_collinear_columns=True, seed=1)
    glm_freq.train(x=predictors, y=response_col, training_frame=X_freq, weights_column=weight_col)
    glm_relevel.train(x=predictors, y=response_col, training_frame=X_relevel, weights_column=weight_col)
    
    pred1 = glm_relevel.predict(X_freq)
    pred2 = glm_relevel.predict(X_relevel)

    print('GLM with the reference level set using relevel_by_frequency()')
    print(glm_freq._model_json['output']['coefficients_table'])
    print('\n')
    print('GLM with the reference level manually set using relevel()')
    print(glm_relevel._model_json['output']['coefficients_table'])


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_ordinal)
else:
    test_ordinal()
