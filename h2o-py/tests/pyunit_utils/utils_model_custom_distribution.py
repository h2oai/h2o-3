import h2o
from . import locate


def check_model_metrics(default_model, custom_model, model_type):
    shd = default_model.scoring_history()
    shc = custom_model.scoring_history()
    for metric in shd:
        if metric in ["timestamp", "duration", "training_deviance", "validation_deviance"]:
            continue
        assert (shd[metric].isnull() == shc[metric].isnull()).all(), \
            "Scoring history is not the same for default and custom %s distribution and %s metric" % (model_type, metric)
        assert (shd[metric].dropna() == shc[metric].dropna()).all(), \
            "Scoring history is not the same for default and custom %s distribution and %s metric." % (model_type, metric)


def dataset_prostate(categorical=True):
    df = h2o.import_file(path=locate("smalldata/prostate/prostate.csv"))
    df = df.drop("ID")
    if categorical:
        df["CAPSULE"] = df["CAPSULE"].asfactor()
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def dataset_iris():
    df = h2o.import_file(path=locate("smalldata/iris/iris_wheader.csv"))
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def regression_model_default(Model, distribution):
    (ftrain, fvalid, ftest) = dataset_prostate(False)
    model = Model(model_id="regression",
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution=distribution)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def regression_model_distribution(Model, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_prostate(False)
    model = Model(model_id="regression_custom",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def binomial_model_default(Model, distribution):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = Model(model_id="binomial",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution=distribution)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def binomial_model_distribution(Model, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = Model(model_id="binomial_custom",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model_default(Model):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = Model(model_id="multinomial_custom", 
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="multinomial")
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model_distribution(Model, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = Model(model_id="multinomial_custom", 
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest

