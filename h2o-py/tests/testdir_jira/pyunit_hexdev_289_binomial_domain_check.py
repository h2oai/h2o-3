from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def domain_check():
    air_train = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_train.show()
    air_test = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_test.show()

    actual_domain = [u'YES',u'NO']
    print("actual domain of the response: {0}".format(actual_domain))

    ### DRF ###
    print()
    print("-------------- DRF:")
    print()
    air_train["IsDepDelayed"] = air_train["IsDepDelayed"].asfactor()
    rf = H2ORandomForestEstimator()
    rf.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"], y="IsDepDelayed", training_frame=air_train)
    computed_domain = rf._model_json['output']['training_metrics']._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    perf = rf.model_performance(test_data=air_test)
    computed_domain = perf._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)


    ### GBM ###
    print()
    print("-------------- GBM:")
    print()

    gbm = H2OGradientBoostingEstimator(distribution="bernoulli")
    gbm.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"], y="IsDepDelayed", training_frame=air_train)
    computed_domain = gbm._model_json['output']['training_metrics']._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    perf = rf.model_performance(test_data=air_test)
    computed_domain = perf._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    ### Deeplearning ###
    print()
    print("-------------- Deeplearning:")
    print()

    dl = H2ODeepLearningEstimator(activation="Tanh", hidden=[2, 2, 2], epochs=10)
    dl.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"], y="IsDepDelayed", training_frame=air_train)
    computed_domain = dl._model_json['output']['training_metrics']._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    perf = dl.model_performance(test_data=air_test)
    computed_domain = perf._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    ### GLM ###
    print()
    print("-------------- GLM:")
    print()

    glm = H2OGeneralizedLinearEstimator(family="binomial")
    glm.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"], y="IsDepDelayed", training_frame=air_train)
    computed_domain = glm._model_json['output']['training_metrics']._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                        "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    perf = glm.model_performance(test_data=air_test)
    computed_domain = perf._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)



if __name__ == "__main__":
    pyunit_utils.standalone_test(domain_check)
else:
    domain_check()
