from builtins import range
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator


def test_kmeans_cv():
    data = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    km_model = H2OKMeansEstimator(k=3, nfolds=3, estimate_k=True)
    km_model.train(x=list(range(4)), training_frame=data)
    centers = km_model.centers()
    print(centers)

    # test cross validation model 3 has centroid stats
    cv_model1 = h2o.get_model(km_model._model_json['output']['cross_validation_models'][0]['name'])
    print(cv_model1)
    assert cv_model1._model_json['output']['training_metrics']['centroid_stats'] is not None

    # test cross validation model 3 has centroid stats
    cv_model2 = h2o.get_model(km_model._model_json['output']['cross_validation_models'][1]['name'])
    print(cv_model2)
    assert cv_model2._model_json['output']['training_metrics']['centroid_stats'] is not None

    # test cross validation model 3 has centroid stats
    cv_model3 = h2o.get_model(km_model._model_json['output']['cross_validation_models'][2]['name'])
    print(cv_model3)
    assert cv_model3._model_json['output']['training_metrics']['centroid_stats'] is not None
    
    # test cross validation metrics does not have centroid stats
    print(km_model._model_json['output']['cross_validation_metrics'])
    assert km_model._model_json['output']['cross_validation_metrics']['centroid_stats'] is None
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_kmeans_cv)
else:
    test_kmeans_cv()
