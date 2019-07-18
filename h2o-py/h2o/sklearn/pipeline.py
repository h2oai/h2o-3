from sklearn.base import BaseEstimator, ClassifierMixin, RegressorMixin, MetaEstimatorMixin, TransformerMixin
from sklearn.pipeline import Pipeline
from sklearn.utils.metaestimators import _BaseComposition

from .wrapper import H2OClusterMixin


class H2OPipeline(Pipeline, H2OClusterMixin):

    def __init__(self, steps, memory=None, type='classification', cluster_args=None):
        super(H2OPipeline, self).__init__(steps, memory=memory)
        self._type = type
        self.init_cluster(**(cluster_args if cluster_args else {}))


class H2OClassifierPipeline(H2OPipeline, ClassifierMixin, MetaEstimatorMixin):

    def __init__(self, steps, memory=None, cluster_args=None):
        super(H2OClassifierPipeline, self).__init__(steps,
                                                    memory=memory,
                                                    type='classification',
                                                    cluster_args=cluster_args)


class H2ORegressorPipeline(H2OPipeline, RegressorMixin, MetaEstimatorMixin):

    def __init__(self, steps, memory=None, cluster_args=None):
        super(H2ORegressorPipeline, self).__init__(steps,
                                                   memory=memory,
                                                   type='regression',
                                                   cluster_args=cluster_args)

