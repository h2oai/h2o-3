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
        cph_py = CoxPHFitter(strata=stratify_by)
    else:
        cph_py = CoxPHFitter()

    for col in stratify_by:
        rossi[col] = rossi[col].astype('category')

    cph_py.fit(rossi, duration_col='week', event_col='arrest')
    cph_py.print_summary()
    rossi_h2o = h2o.H2OFrame(rossi)

    for col in stratify_by:
        rossi_h2o[col] = rossi_h2o[col].asfactor()
    
    cph_h2o = H2OCoxProportionalHazardsEstimator(stop_column="week", stratify_by=stratify_by)
    cph_h2o.train(x=x, y="arrest", training_frame=rossi_h2o)
    
    assert cph_h2o.model_id != ""
    assert cph_h2o.model_id != ""
    assert cph_h2o.formula() == formula, f"Expected formula to be '{formula}' but it was " + cph_h2o.formula()
    
    predH2O = cph_h2o.predict(test_data=rossi_h2o)
    assert len(predH2O) == len(rossi)
    metrics_h2o = cph_h2o.model_performance(rossi_h2o)
    concordance_py = concordance_for_lifelines(cph_py)
    assert abs(concordance_py - metrics_h2o.concordance()) < 0.001
    hazard_h2o = h2o.get_frame(cph_h2o._model_json['output']['baseline_hazard']['name'])
    hazard_h2o_as_pandas = hazard_h2o.as_data_frame(use_pandas=True)

    hazard_py = cph_py.baseline_hazard_
    
    for col_name in hazard_py.columns:
        hazard_py.rename(columns={col_name: str(col_name)}, inplace=True)
    
    print("h2o:")
    print(hazard_h2o_as_pandas.reset_index(drop=True))
    
    print("lifelines:")
    print(hazard_py.reset_index(drop=True))

    hazard_py_reordered_columns = hazard_py.reset_index(drop=True).sort_index(axis=1)
    hazard_h2o_reordered_columns = hazard_h2o_as_pandas.drop('t', axis="columns").reset_index( drop=True).sort_index(axis=1)
    
    assert_frame_equal(hazard_py_reordered_columns, hazard_h2o_reordered_columns, 
                       check_dtype=False, check_index_type=False, check_column_type=False)
    
    survival_h2o = h2o.get_frame(cph_h2o._model_json['output']['baseline_survival']['name'])
    survival_h2o_as_pandas = survival_h2o.as_data_frame(use_pandas=True)

    survival_py = cph_py.baseline_survival_
    
    for col_name in survival_py.columns:
        survival_py.rename(columns={col_name: str(col_name)}, inplace=True)

    survival_py_reordered_columns = survival_py.reset_index(drop=True).sort_index(axis=1)
    survival_h2o_reordered_columns = survival_h2o_as_pandas.drop('t', axis="columns").reset_index( drop=True).sort_index(axis=1)

    print("h2o:")
    print(survival_h2o_as_pandas.reset_index(drop=True))

    print("lifelines:")
    print(survival_py.reset_index(drop=True))

    assert_frame_equal(survival_py_reordered_columns, survival_h2o_reordered_columns,
                       check_dtype=False, check_index_type=False, check_column_type=False)


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

