from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o

from lifelines import CoxPHFitter
from lifelines.datasets import load_rossi
from pandas.testing import assert_frame_equal



from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_concordance_and_baseline():
    rossi = load_rossi()

    without_strata(rossi)
    with_strata(rossi)


def without_strata(rossi):
    check_cox(rossi
              , x=["age", "fin", "race", "wexp", "mar", "paro", "prio"]
              , stratify_by=[]
              , formula="Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio"
              )

def with_strata(rossi):
    check_cox(rossi
              , x=["age", "fin", "race", "wexp", "mar", "paro", "prio"]
              , stratify_by=["race", "mar"]
              , formula="Surv(week, arrest) ~ fin + age + wexp + paro + prio + strata(race) + strata(mar)"
              )

def check_cox(rossi, x, stratify_by, formula):
    if stratify_by:
        cphPy = CoxPHFitter(strata=stratify_by)
    else:
        cphPy = CoxPHFitter()

    for col in stratify_by:
        rossi[col] = rossi[col].astype('category')

    cphPy.fit(rossi, duration_col='week', event_col='arrest')
    cphPy.print_summary()
    rossiH2O = h2o.H2OFrame(rossi)

    for col in stratify_by:
        rossiH2O[col] = rossiH2O[col].asfactor()
    
    cphH2O = H2OCoxProportionalHazardsEstimator(stop_column="week", stratify_by=stratify_by)
    cphH2O.train(x=x, y="arrest", training_frame=rossiH2O)
    
    assert cphH2O.model_id != ""
    assert cphH2O.model_id != ""
    assert cphH2O.formula() == formula, f"Expected formula to be '{formula}' but it was " + cphH2O.formula()
    
    predH2O = cphH2O.predict(test_data=rossiH2O)
    assert len(predH2O) == len(rossi)
    metricsH2O = cphH2O.model_performance(rossiH2O)
    concordancePy = concordance_for_lifelines(cphPy)
    assert abs(concordancePy - metricsH2O.concordance()) < 0.001
    baselineHazardH2O = h2o.get_frame(cphH2O._model_json['output']['baseline_hazard']['name'])
    baselineHazardH2OasPandas = baselineHazardH2O.as_data_frame(use_pandas=True)
    print("h2o:")
    print(baselineHazardH2OasPandas[['C2']].reset_index(drop=True).head(12))
    print("lifelines:")
    print(cphPy.baseline_hazard_.reset_index(drop=True).rename(columns={"baseline hazard": "C2"}).head(12))
    assert_frame_equal(cphPy.baseline_hazard_.reset_index(drop=True).rename(columns={"baseline hazard": "C2"}),
                       baselineHazardH2OasPandas[['C2']].reset_index(drop=True), check_dtype=False)
    # baselineSurvivalH2O = h2o.get_frame(cphH2O._model_json['output']['baseline_survival']['name'])
    # baselineSurvivalH2OasPandas = baselineSurvivalH2O.as_data_frame(use_pandas=True)
    # 
    # print("h2o:")
    # print(baselineSurvivalH2OasPandas[['C2']].reset_index(drop=True).head(12))
    # print("lifelines:")
    # print(cphPy.baseline_survival_.reset_index(drop=True).rename(columns={"baseline survival": "C2"}).head(12))
    # 
    # assert_frame_equal(cphPy.baseline_survival_.reset_index(drop=True).rename(columns={"baseline survival": "C2"}), baselineSurvivalH2OasPandas[['C2']].reset_index(drop=True), check_dtype=False)


# There are different API versions for concordance in lifelines library
def concordance_for_lifelines(cph):
    if ("_model" in cph.__dict__.keys()):
        py_concordance = cph._model._concordance_index_
    elif ("_concordance_index_" in cph.__dict__.keys()):
        py_concordance = cph._concordance_index_
    else:
        py_concordance = cph._concordance_score_
    return py_concordance

    

if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_concordance_and_baseline)
else:
    coxph_concordance_and_baseline()

