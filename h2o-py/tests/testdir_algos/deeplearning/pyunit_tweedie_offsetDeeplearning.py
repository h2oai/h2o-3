import sys
sys.path.insert(1, "../../../")
import h2o

def tweedie_offset(ip,port):

    insurance = h2o.import_frame(h2o.locate("smalldata/glm_test/insurance.csv"))
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
    assert abs(0.5573887-mean_residual_deviance) < 1e-6, "Expected mean residual deviance to be 0.5573887, but got " \
                                                         "{0}".format(mean_residual_deviance)
    predictions = dl.predict(insurance)
    assert abs(47.80020346-predictions[0].mean()) < 1e-6, "Expected mean of predictions to be 47.80020346, but got " \
                                                          "{0}".format(mean_residual_deviance)
    assert abs(2.017399703-predictions[0].min()) < 1e-6, "Expected min of predictions to be 2.017399703, but got " \
                                                          "{0}".format(mean_residual_deviance)
    assert abs(285.0190279-predictions[0].max()) < 1e-6, "Expected max of predictions to be 285.0190279, but got " \
                                                          "{0}".format(mean_residual_deviance)

    # with offset
    dl = h2o.deeplearning(x=insurance[0:3],y=insurance["Claims"],distribution="tweedie",hidden=[1],epochs=1000,
                          train_samples_per_iteration=-1,reproducible=True,activation="Tanh",single_node_mode=False,
                          balance_classes=False,force_load_balance=False,seed=23123,tweedie_power=1.5,
                          score_training_samples=0,score_validation_samples=0,offset_column="offset",
                          training_frame=insurance)
    mean_residual_deviance = dl.mean_residual_deviance()
    assert abs(0.2606853029-mean_residual_deviance) < 1e-6, "Expected mean residual deviance to be 0.5573887, but got " \
                                                         "{0}".format(mean_residual_deviance)
    predictions = dl.predict(insurance)
    assert abs(49.28646452-predictions[0].mean()) < 1e-6, "Expected mean of predictions to be 49.28646452, but got " \
                                                          "{0}".format(mean_residual_deviance)
    assert abs(1.068305274-predictions[0].min()) < 1e-6, "Expected min of predictions to be 1.068305274, but got " \
                                                         "{0}".format(mean_residual_deviance)
    assert abs(397.0244326-predictions[0].max()) < 1e-6, "Expected max of predictions to be 397.0244326, but got " \
                                                         "{0}".format(mean_residual_deviance)
if __name__ == "__main__":
    h2o.run_test(sys.argv, tweedie_offset)
