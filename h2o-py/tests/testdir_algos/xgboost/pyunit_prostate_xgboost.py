from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.xgboost import H2OXGBoostEstimator


def prostate_xgboost():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate = prostate.drop("ID")
    vol = prostate['VOL']
    vol[vol == 0] = None
    gle = prostate['GLEASON']
    gle[gle == 0] = None
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
    
    binomial = H2OXGBoostEstimator(ntrees=5, learn_rate=0.1)
    binomial.train(
        x=list(range(1, prostate.ncol)),
        y="CAPSULE",
        training_frame=prostate,
        validation_frame=prostate
    )

    expected_p_names = ['predict', 'p0', 'p1']
    p = binomial.predict(prostate)
    assert p.nrow == prostate.nrow, "predictions should have same number of rows as features"
    assert p.names == expected_p_names, \
        "Expected assignment frame names to be %s but was %s instead" % (expected_p_names, p.names)
    
    expected_ln_names = [
        'T1.C1', 'T2.C1', 'T3.C1', 'T4.C1', 'T5.C1'
    ]
    ln = binomial.predict_leaf_node_assignment(prostate)
    assert ln.nrow == prostate.nrow, "predictions should have same number of rows as features"
    assert ln.names == expected_ln_names, \
        "Expected assignment frame names to be %s but was %s instead" % (expected_ln_names, ln.names)
    
    lnids = binomial.predict_leaf_node_assignment(prostate, type="Node_ID")
    assert lnids.nrow == prostate.nrow, "predictions should have same number of rows as features"
    assert lnids.names == expected_ln_names, \
        "Expected assignment frame names to be %s but was %s instead" % (expected_ln_names, lnids.names)
    
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    multinomial = H2OXGBoostEstimator(ntrees=4, learn_rate=0.1, distribution="multinomial")
    multinomial.train(x=list(range(1, 4)), y="class", training_frame=iris, validation_frame=iris)
    expected_ln_names_multi = [
        'T1.C1', 'T1.C2', 'T1.C3', 'T2.C1', 'T2.C2', 'T2.C3', 'T3.C1', 'T3.C2', 'T3.C3', 'T4.C1', 'T4.C2', 'T4.C3'
    ]
    ln = multinomial.predict_leaf_node_assignment(iris)
    assert ln.nrow == iris.nrow, "predictions should have same number of rows as features"
    assert ln.names == expected_ln_names_multi, \
        "Expected assignment frame names to be %s but was %s instead" % (expected_ln_names_multi, ln.names)
    
    lnids = multinomial.predict_leaf_node_assignment(iris, type="Node_ID")
    assert lnids.nrow == iris.nrow, "predictions should have same number of rows as features"
    assert lnids.names == expected_ln_names_multi, \
        "Expected assignment frame names to be %s but was %s instead" % (expected_ln_names_multi, lnids.names)


if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_xgboost)
else:
    prostate_xgboost()
