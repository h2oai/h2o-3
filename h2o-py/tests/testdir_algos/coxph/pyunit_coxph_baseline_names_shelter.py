from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o

from lifelines import CoxPHFitter
from pandas.testing import assert_frame_equal
from pandas import read_csv

import numpy as np

from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_concordance_and_baseline():
    shelter = read_csv(pyunit_utils.locate("smalldata/coxph_test/shelter.csv"), low_memory=False)
    shelter.drop("animal_id", axis=1, inplace=True)
    for colname in ["animal_type", "animal_breed", "activity_number", "census_tract", "activity_sequence"
                    , "intake_condition1", "intake_condition2", "intake_condition3", "council_district"
                    , "intake_type", "intake_subtype", "due_out", "outcome_type", "outcome_subtype"
                    , "intake_condition", "outcome_condition", "chip_status"]:
        shelter[colname] = shelter[colname].astype("category")
    for colname in ["end_ts", "intake_date", "intake_time", "start_ts"]:
        shelter[colname] = shelter[colname].astype("datetime64")

    without_strata(shelter)
    with_strata(shelter)
    with_strata_one_column(shelter)


def without_strata(shelter):
    check_cox(shelter
              , x=["intake_condition", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , expected_hazard_cols=["baseline hazard"]
              , expected_survival_cols=["baseline survival"]
              , stratify_by=[]
              )


def with_strata(shelter):
    baseline_cols = ["(" + c1 + ", " + c2 + ")" for c1 in ["CONFISCATED", "OWNER SURRENDER", "STRAY"]
                     for c2 in ["NONE", "TREATABLE MANAGEABLE", "TREATABLE REHABILITABLE", "UNHEALTHY UNTREATABLE"]]
    
    check_cox(shelter
              , x=["intake_condition1", "intake_condition2", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , expected_hazard_cols=baseline_cols
              , expected_survival_cols=baseline_cols
              , stratify_by=["intake_type", "intake_condition1"]
              )
    
    
def with_strata_one_column(shelter):
    baseline_cols = ["(CONFISCATED)", "(OWNER SURRENDER)", "(STRAY)"]
    check_cox(shelter
              , x=["intake_condition1", "intake_condition2", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , expected_hazard_cols=baseline_cols
              , expected_survival_cols=baseline_cols
              , stratify_by=["intake_type"]
              )
   

def check_cox(shelter, x, expected_hazard_cols, expected_survival_cols, stratify_by=None, weight=None):
    shelter = shelter[x + ['event'] + ([weight] if weight else [])]
    
    for col in stratify_by:
        shelter[col] = shelter[col].astype('category')

    shelter_h2o = h2o.H2OFrame(shelter)

    for col in stratify_by:
        shelter_h2o[col] = shelter_h2o[col].asfactor()
    
    cph_h2o = H2OCoxProportionalHazardsEstimator(stop_column="surv_hours", stratify_by=stratify_by)
    cph_h2o.train(x=x, y="event", weights_column=weight, training_frame=shelter_h2o)
    
    assert cph_h2o.model_id != ""
    
    pred_h2o = cph_h2o.predict(test_data=shelter_h2o)
    assert len(pred_h2o) == len(shelter)
    hazard_h2o_as_pandas = cph_h2o.baseline_hazard_frame.as_data_frame(use_pandas=True)

    hazard_h2o_reordered_columns = hazard_h2o_as_pandas.drop('t', axis="columns")\
                                                       .reset_index( drop=True)\
                                                       .sort_index(axis=1)

    assert hazard_h2o_reordered_columns.columns.values.tolist() == expected_hazard_cols
  
    survival_h2o_as_pandas = cph_h2o.baseline_survival_frame.as_data_frame(use_pandas=True)

    survival_h2o_reordered_columns = survival_h2o_as_pandas.drop('t', axis="columns")\
                                                       .reset_index( drop=True)\
                                                       .sort_index(axis=1)

    assert survival_h2o_reordered_columns.columns.values.tolist() == expected_survival_cols
  

# There are different API versions for concordance in lifelines library
def concordance_for_lifelines(cph):
    if "_model" in cph.__dict__.keys():
        py_concordance = cph._model._concordance_index_
    elif "_concordance_index_" in cph.__dict__.keys():
        py_concordance = cph._concordance_index_
    else:
        py_concordance = cph._concordance_score_
    return py_concordance


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_concordance_and_baseline)
else:
    coxph_concordance_and_baseline()

