"""
A GBM model builder.
"""

from h2o_model_builder import H2OModelBuilder


class H2OGBMBuilder(H2OModelBuilder):
    """
    Build a new GBM model.

    Example Usage:

        from h2o.model.gbm_builder import H2OGBM    # import this builder
        my_gbm = H2OGBM()                           # create a new gbm object
        my_gbm.x = [0,1,2,3]                        # fill in parameters:
        my_gbm.y = 4
        my_gbm.training_frame = <training_frame>
        my_gbm.ntrees = 100
        my_gbm.max_depth = 5
        my_gbm.learning_rate = 0.01

        my_gbm.fit()                                  # perform the model fit
    """

    def __init__(self, x=None, y=None, training_frame=None, key=None,
                 loss=("AUTO", "bernoulli"), ntrees=50, max_depth=5, learn_rate=0.1,
                 nbins=20, group_split=True, variable_importance=False,
                 validation_frame=None, balance_classes=False, max_after_balance_size=1,
                 seed=None):
        """
        Instantiate a GBMBuilder.
        :param x: Predictor columns (may be indices or strings)
        :param y: Response column (may be an index or a string)
        :param training_frame: An object of type H2OFrame
        :param key: The output name of the model.
        :param loss: Bernoulli for binary outcomes, AUTO for multinomial and regression
        :param ntrees:  The number of trees in the GBM.
        :param max_depth:   The maximum depth a tree will grow to.
        :param learn_rate:  The learning rate (also called shrinkage).
        :param nbins:   The bins per histogram. (Numeric columns are binned)
        :param group_split: Group splitting on categorical columns.
        :param variable_importance: Compute variable importance
        :param validation_frame: Score on a validation frame.
        :param balance_classes: Balance response classes.
        :param max_after_balance_size:  Maximum size of the dataset after balancing.
        :param seed: A random seed.
        :return: A new GBMBuilder.
        """
        super(H2OGBMBuilder, self).__init__(locals(), "gbm", training_frame)
        self.x = x
        self.y = y
        self.training_frame = training_frame
        self.validation_frame = None
        self.loss = "AUTO" if isinstance(loss, tuple) else loss
        self.ntrees = ntrees
        self.max_depth = max_depth
        self.learn_rate = learn_rate
        self.nbins = nbins
        self.group_split = group_split
        self.variable_importance = variable_importance
        self.validation_frame = validation_frame
        self.balance_classes = balance_classes
        self.max_after_balance_size = max_after_balance_size
        self.seed = seed