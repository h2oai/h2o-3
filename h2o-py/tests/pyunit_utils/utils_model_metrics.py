import h2o
from . import locate


class CustomMaeFunc:
    def map(self, pred, act, w, o, model):
        return [abs(act[0] - pred[0]), 1]

    def reduce(self, l, r):
        return [l[0] + r[0], l[1] + r[1]]

    def metric(self, l):
        return l[0] / l[1]


class CustomRmseFunc:
    def map(self, pred, act, w, o, model):
        idx = int(act[0])
        err = 1 - pred[idx + 1] if idx + 1 < len(pred) else 1
        return [err * err, 1]

    def reduce(self, l, r):
        return [l[0] + r[0], l[1] + r[1]]

    def metric(self, l):
        # Use Java API directly
        import java.lang.Math as math
        return math.sqrt(l[0] / l[1])

class CustomLoglossFunc:
    def map(self, pred, act, w, o, model):
        import water.util.MathUtils as math
        idx = int(act[0])
        err = 1 - pred[idx + 1] if idx + 1 < len(pred) else 1
        return [w * math.logloss(err), w]

    def reduce(self, l, r):
        return [l[0] + r[0], l[1] + r[1]]

    def metric(self, l):
        return l[0] / l[1]

class CustomNullFunc:
    def map(self, pred, act, w, o, model):
        return []

    def reduce(self, l, r):
        return []

    def metric(self, l):
        return 0


class CustomOneFunc:
    def map(self, pred, act, w, o, model):
        return  []

    def reduce(self, l, r):
        return []

    def metric(self, l):
        return 1

CustomOneFuncStr = '''
class CustomOneFunc:
    def map(self, pred, act, w, o, model):
        return  []

    def reduce(self, l, r):
        return []

    def metric(self, l):
        return 1
'''

def assert_metrics_equal(metric, metric_name1, metric_name2, msg=None):
    metric_name1 = metric_name1 if metric_name1 in metric._metric_json else metric_name1.upper()
    metric_name2 = metric_name2 if metric_name2 in metric._metric_json else metric_name2.upper()
    metric_value1 = metric._metric_json[metric_name1]
    metric_value2 = metric._metric_json[metric_name2]
    assert metric_value1 == metric_value2, "{} {}={} {}={}".format(msg, metric_name1, metric_value1, metric_name2, metric_value2)


def assert_all_metrics_equal(model, f_test, metric_name, value):
    mm_train = model.model_performance(train=True)
    assert mm_train._metric_json["custom_metric_value"] == value, \
        "{} metric on training data should be {}".format(metric_name, value)

    mm_valid = model.model_performance(valid=True)
    assert mm_valid._metric_json["custom_metric_value"] == value, \
        "{} metric on validation data should be {}".format(metric_name, value)

    # Make a new model metric
    mm_test = model.model_performance(test_data=f_test)
    assert mm_test._metric_json["custom_metric_value"] == value, \
        "{} metric on validation data should be {}".format(metric_name, value)


def assert_scoring_history(model, metric_name1, metric_name2, msg=None):
    scoring_history = model.scoring_history()
    sh1 = scoring_history[metric_name1]
    sh2 = scoring_history[metric_name2]
    assert (sh1.isnull() == sh2.isnull()).all(), msg
    assert (sh1.dropna() == sh2.dropna()).all(), msg


def assert_correct_custom_metric(model, f_test, metric_name, msg=None):
    # Check model performance on training data
    mm_train = model.model_performance(train=True)
    assert_metrics_equal(mm_train, metric_name, "custom_metric_value",
                         "{}: Train metric should match custom metric".format(msg))
    # Check model performance on validation data
    mm_valid = model.model_performance(valid=True)
    assert_metrics_equal(mm_valid, metric_name, "custom_metric_value",
                         "{}: Validation metric should match custom metric".format(msg))
    # Make a new model metric
    mm_test = model.model_performance(test_data=f_test)
    assert_metrics_equal(mm_test, metric_name, "custom_metric_value",
                         "{}: Test metric should match custom metric".format(msg))

    # Check scoring history
    assert_scoring_history(model, "training_{}".format(metric_name), "training_custom",
                           "{}: Scoring history for training data should match".format(msg))
    assert_scoring_history(model, "validation_{}".format(metric_name), "validation_custom",
                           "{}: Scoring history for validation data should match".format(msg))


def dataset_prostate():
    df = h2o.import_file(path=locate("smalldata/prostate/prostate.csv"))
    df = df.drop("ID")
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def dataset_iris():
    df = h2o.import_file(path=locate("smalldata/iris/iris_wheader.csv"))
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


# Regression Model fixture
def regression_model(ModelType, custom_metric_func, params={}):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func,  **params)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


# Binomial model fixture
def binomial_model(ModelType, custom_metric_func, params={}):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="binomial",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func,  **params)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model(ModelType, custom_metric_func, params={}):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = ModelType(model_id="multinomial",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func, **params)
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest
