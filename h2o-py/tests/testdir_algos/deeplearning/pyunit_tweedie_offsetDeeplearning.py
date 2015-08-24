import sys
sys.path.insert(1, "../../../")
import h2o, tests

def tweedie_offset(ip,port):

    insurance = h2o.import_file(h2o.locate("smalldata/glm_test/insurance.csv"))
    insurance["offset"] = insurance["Holders"].log()
    insurance["Group"] = insurance["Group"].asfactor()
    insurance["Age"] = insurance["Age"].asfactor()
    insurance["District"] = insurance["District"].asfactor()

    # without offset
    dl = h2o.deeplearning(x=insurance[0:3],y=insurance["Claims"],distribution="tweedie",hidden=[1],epochs=1000,
                          train_samples_per_iteration=-1,reproducible=True,activation="Tanh",single_node_mode=False,
                          balance_classes=False,force_load_balance=False,seed=23123,tweedie_power=1.5,
                          score_training_samples=0,score_validation_samples=0)

    mean_residual_deviance = dl.mean_residual_deviance()
    assert abs(0.561641366536-mean_residual_deviance) < 1e-6, "Expected mean residual deviance to be 0.561641366536, but got " \
                                                         "{0}".format(mean_residual_deviance)
    predictions = dl.predict(insurance)
    assert abs(47.6819999424-predictions[0].mean()) < 1e-6, "Expected mean of predictions to be 47.6819999424, but got " \
                                                          "{0}".format(predictions[0].mean())
    assert abs(1.90409304033-predictions[0].min()) < 1e-6, "Expected min of predictions to be 1.90409304033, but got " \
                                                          "{0}".format(predictions[0].min())
    assert abs(280.735054543-predictions[0].max()) < 1e-6, "Expected max of predictions to be 280.735054543, but got " \
                                                          "{0}".format(predictions[0].max())

    # with offset
    dl = h2o.deeplearning(x=insurance[0:3],y=insurance["Claims"],distribution="tweedie",hidden=[1],epochs=1000,
                          train_samples_per_iteration=-1,reproducible=True,activation="Tanh",single_node_mode=False,
                          balance_classes=False,force_load_balance=False,seed=23123,tweedie_power=1.5,
                          score_training_samples=0,score_validation_samples=0,offset_column="offset",
                          training_frame=insurance)
    mean_residual_deviance = dl.mean_residual_deviance()
    assert abs(0.261065520191-mean_residual_deviance) < 1e-6, "Expected mean residual deviance to be 0.261065520191, but got " \
                                                         "{0}".format(mean_residual_deviance)
    predictions = dl.predict(insurance)
    assert abs(49.2939039783-predictions[0].mean()) < 1e-6, "Expected mean of predictions to be 49.2939039783, but got " \
                                                          "{0}".format(predictions[0].mean())
    assert abs(1.07391126487-predictions[0].min()) < 1e-6, "Expected min of predictions to be 1.07391126487, but got " \
                                                          "{0}".format(predictions[0].min())
    assert abs(397.328758591-predictions[0].max()) < 1e-6, "Expected max of predictions to be 397.328758591, but got " \
                                                          "{0}".format(predictions[0].max())
if __name__ == "__main__":
    tests.run_test(sys.argv, tweedie_offset)
