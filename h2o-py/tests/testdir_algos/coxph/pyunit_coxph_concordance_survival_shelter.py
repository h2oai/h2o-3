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
        shelter[colname] = shelter[colname].cat.codes
    for colname in ["end_ts", "intake_date", "intake_time", "start_ts"]:
        shelter[colname] = shelter[colname].astype("datetime64")

    without_strata(shelter)
    with_strata(shelter)
    with_strata_one_column(shelter)
    with_strata_and_weights(shelter)


def without_strata(shelter):
    check_cox(shelter
              , x=["intake_condition", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , stratify_by=[]
              , expected_formula="Surv(surv_hours, event) ~ intake_condition + intake_type + animal_breed + chip_status"
              )


def with_strata(shelter):
    check_cox(shelter
              , x=["intake_condition1", "intake_condition2", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , stratify_by=["intake_type", "intake_condition1"]
              , expected_formula="Surv(surv_hours, event) ~ "
                                 "intake_condition2 + animal_breed + chip_status + "
                                 "strata(intake_type) + strata(intake_condition1)"
              )
    
    
def with_strata_one_column(shelter):
    check_cox(shelter
              , x=["intake_condition1", "intake_condition2", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , stratify_by=["intake_type"]
              , expected_formula="Surv(surv_hours, event) ~ "
                                 "intake_condition1 + intake_condition2 + animal_breed + chip_status + "
                                 "strata(intake_type)"
              )
   
    
def with_strata_and_weights(shelter):
    shelter["weight"] = np.random.normal(0.5, 0.2, shelter.index.size)
    shelter["weight"] = shelter["weight"].abs()
    
    check_cox(shelter
              , x=["intake_condition1", "intake_condition2", "intake_type", "animal_breed", "chip_status", "surv_hours"]
              , stratify_by=["intake_type", "intake_condition1"]
              , expected_formula="Surv(surv_hours, event) ~ "
                                 "intake_condition2 + animal_breed + chip_status + "
                                 "strata(intake_type) + strata(intake_condition1)"
              , weight="weight"
              )


def check_cox(shelter, x, expected_formula, stratify_by=None, weight=None):
    shelter = shelter[x + ['event'] + ([weight] if weight else [])]
    
    cph_py = CoxPHFitter(strata=stratify_by) if stratify_by else CoxPHFitter()

    for col in stratify_by:
        shelter[col] = shelter[col].astype('category')

    cph_py.fit(shelter, duration_col='surv_hours', event_col='event', weights_col=weight)
    cph_py.print_summary()
    
    shelter_h2o = h2o.H2OFrame(shelter)

    for col in stratify_by:
        shelter_h2o[col] = shelter_h2o[col].asfactor()
    
    cph_h2o = H2OCoxProportionalHazardsEstimator(stop_column="surv_hours", stratify_by=stratify_by)
    cph_h2o.train(x=x, y="event", weights_column=weight, training_frame=shelter_h2o)
    
    assert cph_h2o.model_id != ""
    assert cph_h2o.formula() == \
           expected_formula, "Expected formula to be '" + expected_formula + "' but it was " + cph_h2o.formula()
    
    pred_h2o = cph_h2o.predict(test_data=shelter_h2o)
    assert len(pred_h2o) == len(shelter)
    metrics_h2o = cph_h2o.model_performance(shelter_h2o)
    concordance_py = concordance_for_lifelines(cph_py)
    assert abs(concordance_py - metrics_h2o.concordance()) < 0.001
    hazard_h2o = h2o.get_frame(cph_h2o._model_json['output']['baseline_hazard']['name'])
    hazard_h2o_as_pandas = hazard_h2o.as_data_frame(use_pandas=True)

    hazard_py = cph_py.baseline_hazard_
    
    for col_name in hazard_py.columns:
        if (isinstance(col_name, int)):
            new_name = "({0})".format(col_name)
        else:
            new_name = str(col_name)
        hazard_py.rename(columns={col_name: new_name}, inplace=True)
    
    hazard_py_reordered_columns = hazard_py.reset_index(drop=True)\
                                           .sort_index(axis=1)
    hazard_h2o_reordered_columns = hazard_h2o_as_pandas.drop('t', axis="columns")\
                                                       .reset_index( drop=True)\
                                                       .sort_index(axis=1)
    
    assert_frame_equal(hazard_py_reordered_columns, hazard_h2o_reordered_columns, 
                       check_dtype=False, check_index_type=False, check_column_type=False, check_less_precise=True)
    
    survival_h2o = h2o.get_frame(cph_h2o._model_json['output']['baseline_survival']['name'])
    survival_h2o_as_pandas = survival_h2o.as_data_frame(use_pandas=True)

    survival_py = cph_py.baseline_survival_

    for col_name in survival_py.columns:
        if (isinstance(col_name, int)):
            new_name = "({0})".format(col_name)
        else:
            new_name = str(col_name)
        survival_py.rename(columns={col_name: new_name}, inplace=True)

    survival_py_reordered_columns = survival_py.reset_index(drop=True).sort_index(axis=1)
    survival_h2o_reordered_columns = survival_h2o_as_pandas.drop('t', axis="columns").reset_index( drop=True).sort_index(axis=1)

    assert_frame_equal(survival_py_reordered_columns, survival_h2o_reordered_columns,
                       check_dtype=False, check_index_type=False, check_column_type=False, check_less_precise=True)


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

