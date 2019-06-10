import h2o
from . import locate


class CustomDistributionGaussian:

    def link(self, f):
        return f
    
    def inversion(self, f):
        return f
    
    def deviance(self, w, y, f):
        return w * (y - f) * (y - f)

    def init(self, w, o, y):
        return [w * (y - o), w]
    
    def gradient(self, y, f):
        return y - f
    
    def gamma(self, w, y, z, f):
        return [w * z, w]


class CustomDistributionGaussianWrong:

    def link(self, f):
        return f

    def inversion(self, f):
        return f

    def deviance(self, w, y, f):
        return w * (y - f) * (y - f)

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return (y - f) * (y - f)

    def gamma(self, w, y, z, f):
        return [w * z, w]


class CustomDistributionBernoulli:

    def link(self, f):
        def log(x):
            import java.lang.Math as Math
            min_log = -19
            x = Math.max(0, x)
            if x == 0:
                return min_log
            else:
                return Math.max(min_log, Math.log(x))
        return log(f / (1 - f))

    def inversion(self, f):
        def exp(x):
            import java.lang.Math as Math
            return Math.min(1e19, Math.exp(x))
        return 1 / (1 + exp(-f))

    def deviance(self, w, y, f):
        def log(x):
            import java.lang.Math as Math
            min_log = -19
            x = Math.max(0, x)
            if x == 0:
                return min_log
            else:
                return Math.max(min_log, Math.log(x))
        return -2 * w * (y * log(f) + (1 - y) * log(1 - f))

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - self.inversion(f)

    def gamma(self, w, y, z, f):
        ff = y - z
        return [w * z, w * ff * (1 - ff)]
    

class CustomDistributionMultinomial:

    def link(self, f):
        def log(x):
            import java.lang.Math as Math
            min_log = -19
            x = Math.max(0, x)
            if x == 0:
                return min_log
            else:
                return Math.max(min_log, Math.log(x))
        return log(f)

    def inversion(self, f):
        def exp(x):
            import java.lang.Math as Math
            return Math.min(1e19, Math.exp(x))
        return exp(f)

    def deviance(self, w, y, f):
        return w * (y - f) * (y - f)

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - f

    def gamma(self, w, y, z, f):
        import java.lang.Math as math
        absz = math.abs(z)
        return [w * z, w * (absz * (1 - absz))]
    

class CustomDistributionNull:

    def link(self, f):
        return 0

    def inversion(self, f):
        return 0

    def deviance(self, w, y, f):
        return 0

    def init(self, w, o, y):
        return [0, 0]

    def gradient(self, y, f):
        return 0

    def gamma(self, w, y, z, f):
        return 0


def dataset_prostate():
    df = h2o.import_file(path=locate("smalldata/prostate/prostate.csv"))
    df = df.drop("ID")
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def dataset_iris():
    df = h2o.import_file(path=locate("smalldata/iris/iris_wheader.csv"))
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def regression_model_default(ModelType):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression",
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="gaussian")
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def regression_model_distribution(ModelType, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression_custom",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def bernoulli_model_default(ModelType):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="binomial",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="bernoulli")
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def bernoulli_model_distribution(ModelType, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="binomial_custom",
                      ntrees=3,
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model_default(ModelType):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = ModelType(model_id="multinomial_custom", 
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="multinomial")
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model_distribution(ModelType, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = ModelType(model_id="multinomial_custom", 
                      ntrees=3, 
                      max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest

