from ..h2o import random_forest
from .estimator_base import H2OEstimator

class H2ORandomForestEstimator(H2OEstimator):
  def __init__(self,mtries=None,sample_rate=None,build_tree_one_node=None,ntrees=None,
               max_depth=None,min_rows=None,nbins=None,nbins_cats=None,
               binomial_double_trees=None,balance_classes=None,max_after_balance_size=None,
               seed=None,offset_column=None,weights_column=None):
    super(H2ORandomForestEstimator, self).__init__()
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self._estimator_type="regressor"

  def fit(self,X,y=None,**params):
    if y is not None:
      if y.isfactor(): self._estimator_type="classifier"
    self.__dict__=random_forest(x=X,y=y,**self.parms).__dict__.copy()