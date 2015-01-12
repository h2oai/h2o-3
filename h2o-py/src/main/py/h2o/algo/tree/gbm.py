"""
This module is a simple wrapper around a Gradient Boosted Machine.
"""

from ..model import ModelBase
from h2o.algo import *

class H2OGBM(ModelBase):
    """
    A Gradient Boosted Machine (GBM) is a type of tree model.
    """

    def __init__(self, dataset, x, ntrees=50, shrinkage=0.1, interaction_depth=5,
                 distribution="AUTO", validation_dataset=None):
        params = locals()  # called immediately to prevent pollution from other local vars

        # super(H2OGBM, self).__init__()
        if not isinstance(dataset, H2OFrame):
            raise ValueError("`dataset` must be a H2OFrame not " + str(type(dataset)))

        super(H2OGBM, self).__init__()


        self.dataset = dataset
        if not dataset[x]:
            raise ValueError(x + " must be column in " + str(dataset))
        self.x = x
        if not (0 <= ntrees <= 1000000):
            raise ValueError("ntrees must be between 0 and a million")
        self.ntrees = ntrees
        if not (0.0 <= shrinkage <= 1.0): raise ValueError(
            "shrinkage must be between 0 and 1")
        self.shrinkage = 0.1
        if not (1 <= interaction_depth):
            raise ValueError("interaction_depth must be at least 1")
        self.interaction_depth = interaction_depth
        self.distribution = distribution
        fr = _send_frame(dataset)
        if validation_dataset:
            vfr = _send_frame(validation_dataset)

        self._model = H2OCONN.GBM(distribution, shrinkage, ntrees, interaction_depth, x,
                                  fr, vfr)
        H2OCONN.Remove(fr)

