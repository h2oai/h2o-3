import sys
sys.path.insert(1, "../../")
import h2o

def domain_check(ip, port):
    

    air_train = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_train.show()
    air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_test.show()

    actual_domain = [u'YES',u'NO']
    print "actual domain of the response: {0}".format(actual_domain)

    ### DRF ###
    print
    print "-------------- DRF:"
    print
    rf = h2o.random_forest(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth",
                                        "fDayOfWeek"]], y=air_train ["IsDepDelayed"].asfactor(), training_frame=air_train)
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
    print
    print "-------------- GBM:"
    print
    gbm = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth","fDayOfWeek"]],
                  y=air_train["IsDepDelayed"].asfactor(), training_frame=air_train, distribution="bernoulli")
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
    print
    print "-------------- Deeplearning:"
    print
    dl = h2o.deeplearning(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth","fDayOfWeek"]],
                     y=air_train["IsDepDelayed"].asfactor(), training_frame = air_train, activation = "Tanh",
                     hidden = [2, 2, 2], epochs = 10)
    computed_domain = dl._model_json['output']['training_metrics']._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    perf = rf.model_performance(test_data=air_test)
    computed_domain = perf._metric_json['domain']
    domain_diff = list(set(computed_domain) - set(actual_domain))
    assert not domain_diff, "There's a difference between the actual ({0}) and the computed ({1}) domains of the " \
                            "The difference is {2}".format(actual_domain, computed_domain, domain_diff)

    ### GLM ###
    print
    print "-------------- GLM:"
    print
    glm = h2o.glm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]],
                  y=air_train["IsDepDelayed"], training_frame=air_train , family="binomial")
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
    h2o.run_test(sys.argv, domain_check)
