import sys, shutil
sys.path.insert(1, "../../../")
import h2o

def cars_checkpoint(ip,port):

    cars = h2o.upload_file(h2o.locate("smalldata/junit/cars_20mpg.csv"))
    predictors = ["displacement","power","weight","acceleration","year"]
    response_col = "economy"

    # build first model
    model1 = h2o.random_forest(x=cars[predictors],y=cars[response_col],ntrees=10,max_depth=2, min_rows=10)

    # continue building the model
    model2 = h2o.random_forest(x=cars[predictors],y=cars[response_col],ntrees=11,max_depth=3, min_rows=9,r2_stopping=0.8,
                               checkpoint=model1._id)

    #   erroneous, not MODIFIABLE_BY_CHECKPOINT_FIELDS
    # PUBDEV-1833

    #   mtries
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],mtries=2,checkpoint=model1._id)
        assert False, "Expected model-build to fail because mtries not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   sample_rate
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],sample_rate=0.5,checkpoint=model1._id)
        assert False, "Expected model-build to fail because sample_rate not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins_cats
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],nbins_cats=99,checkpoint=model1._id)
        assert False, "Expected model-build to fail because nbins_cats not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],nbins=99,checkpoint=model1._id)
        assert False, "Expected model-build to fail because nbins not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   balance_classes
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],balance_classes=True,checkpoint=model1._id)
        assert False, "Expected model-build to fail because balance_classes not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nfolds
    try:
        model = h2o.random_forest(y=cars[response_col], x=cars[predictors],nfolds=3,checkpoint=model1._id)
        assert False, "Expected model-build to fail because nfolds not modifiable by checkpoint"
    except EnvironmentError:
        assert True


if __name__ == "__main__":
    h2o.run_test(sys.argv, cars_checkpoint)
