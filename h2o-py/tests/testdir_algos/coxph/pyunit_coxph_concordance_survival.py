from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o

from lifelines import CoxPHFitter
from lifelines.datasets import load_rossi



from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator


def coxph_concordance_and_baseline():
    rossi = load_rossi()

    # without_strata(rossi)
    with_strata(rossi)


def without_strata(rossi):
    cph = CoxPHFitter()
    cph.fit(rossi, duration_col='week', event_col='arrest')
    cph.print_summary()
    rossiH2O = h2o.H2OFrame(rossi)
    cphH2O = H2OCoxProportionalHazardsEstimator(stop_column="week")
    cphH2O.train(x=["age", "fin", "race", "wexp", "mar", "paro", "prio"], y="arrest", training_frame=rossiH2O)
    assert cphH2O.model_id != ""
    assert cphH2O.formula() == "Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio", \
        "Expected formula to be 'Surv(week, arrest) ~ fin + age + race + wexp + mar + paro + prio' but it was " + cphH2O.formula()
    predH2O = cphH2O.predict(test_data=rossiH2O)
    assert len(predH2O) == len(rossi)
    metricsH2O = cphH2O.model_performance(rossiH2O)
    py_concordance = concordance_for_lifelines(cph)
    
    assert abs(py_concordance - metricsH2O.concordance()) < 0.001
    
    print(cph.baseline_survival_)
    # print(cphH2O._model_json['output'].keys())
    # print(cphH2O._model_json['output']['var_cumhaz_2'])
    # print(len(cphH2O._model_json['output']['cumhaz_0']))
    # print(cphH2O._model_json['output']['baseline_hazard'])
    # frame = h2o.get_frame(cphH2O._model_json['output']['baseline_hazard']['name'])

    print(frame.nrows)
    for i in range(0, frame.nrows):
        print(frame.as_data_frame().loc[i, :])


# There are different API versions for concordance in lifelines library
def concordance_for_lifelines(cph):
    if ("_model" in cph.__dict__.keys()):
        py_concordance = cph._model._concordance_index_
    elif ("_concordance_index_" in cph.__dict__.keys()):
        py_concordance = cph._concordance_index_
    else:
        py_concordance = cph._concordance_score_
    return py_concordance

def with_strata(rossi):
    cph = CoxPHFitter(strata=["race", "mar"])
    cph.fit(rossi, duration_col='week', event_col='arrest')
    cph.print_summary()
    rossiH2O = h2o.H2OFrame(rossi)
    
    print(rossiH2O)
    
    rossiH2O['race'] = rossiH2O['race'].asfactor()
    rossiH2O['mar'] = rossiH2O['mar'].asfactor()
    
    cphH2O = H2OCoxProportionalHazardsEstimator(stop_column="week", stratify_by=['race', 'mar'])
    cphH2O.train(x=["age", "fin", "wexp", "prio", "race", "mar"], y="arrest", training_frame=rossiH2O)
    assert cphH2O.model_id != ""
    assert cphH2O.formula() == "Surv(week, arrest) ~ fin + age + wexp + prio + strata(race) + strata(mar)", \
        "Expected formula to be 'Surv(week, arrest) ~ fin + age + wexp + prio + strata(race) + strata(mar)' but it was " + cphH2O.formula()
    predH2O = cphH2O.predict(test_data=rossiH2O)
    assert len(predH2O) == len(rossi)
    metricsH2O = cphH2O.model_performance(rossiH2O)

   
    assert abs(concordance_for_lifelines(cph) - metricsH2O.concordance()) < 0.003
    print(cph.baseline_survival_)
    print(cphH2O._model_json['output'].keys())
    print(cphH2O._model_json['output']['var_cumhaz_2'])
    print(len(cphH2O._model_json['output']['cumhaz_0']))
    print(cphH2O._model_json['output']['baseline_hazard'])
    frame = h2o.get_frame(cphH2O._model_json['output']['baseline_hazard']['name'])
    
    print(frame.nrows)
    for i in range(0, frame.nrows):
        print(frame.as_data_frame().loc[i, :])


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_concordance_and_baseline)
else:
    coxph_concordance_and_baseline()

