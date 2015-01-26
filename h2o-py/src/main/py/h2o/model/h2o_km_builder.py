"""
A KMeans model builder.
"""

from h2o_model_builder import H2OModelBuilder


class H2OKMeansBuilder(H2OModelBuilder):
    """
    Build a new KMeans model.

    Example Usage:

        from h2o.model.km_builder import H2OKMeans    # import this builder

        my_km = H2OKMeans()                           # create a new kmeans object
        my_km.x = [0,1,2,3]                           # fill in parameters:
        my_km.training_frame = <training_frame>
        my_km.k = 5
        my_km.max_iterations = 100
        my_km.init = "PlusPlus"

        my_km.fit()                                   # perform the model fit
    """
    
    def __init__(self, x=None, k=5, training_frame=None, key=None, max_iterations=1000,
                 standardize=True, init=("Furthest", "Random", "PlusPlus"), seed=None):
        """
        Instantiate a KMeansBuilder.
        :param x: Columns to use for clustering (may be indices or strings).
        :param k: Number of clusters (must be between 1 and 1e7 inclusive).
        :param training_frame: An object of type H2OFrame.
        :param key: The output name of the model.
        :param max_iterations: The maximum number of iterations allowed
                               (must be between 0 and 1e6 inclusive).
        :param standardize: Whether data should be standardized before clustering.
        :param init: How to select initial set of cluster points. Random for random
                     initialization, Furthest for initialization at furthest point from
                     successive centers, and PlusPlus for k-means++.
        :param seed: A random seed.
        :return: A new KMeansBuilder.
        """
        super(H2OKMeansBuilder, self).__init__(locals(), "kmeans", training_frame)
        self.x = x
        self.k = k
        self.training_frame = training_frame
        self.max_iterations = max_iterations
        self.standardize = standardize
        self.init = "Random" if isinstance(init, tuple) else init
        self.seed = seed
        self.validation_frame = None
