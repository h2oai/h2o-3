from .transform_base import H2OTransformer
from ..h2o import prcomp

class H2OPCA(H2OTransformer):
  """
  Principal Component Analysis
  """
  def __init__(self, n_components=None,max_iterations=None,seed=None,
               use_all_factor_levels=False,pca_method=("GramSVD", "Power", "GLRM")):
    """
    :param n_components: The number of components to compute.
    :param max_iterations: Max iterations in each power loop. Capped at 1e6.
    :param seed: Used for initializing right singular vectors at the start of each power iteration.
    :param use_all_factor_levels: A boolean value indicating whether all factor levels should be
           included in each categorical column expansion. If FALSE, the indicator column
           correpsonding to the first level of every categorical variable will be dropped.
    :param pca_method: Method used to compute PCA. Power and GLRM are still experimental.
    :return: An instance of H2OPCA transform.
    """
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["pca_method"]="GramSVD" if isinstance(pca_method,tuple) else pca_method
    self.pca_model=None

  def fit(self,X,y=None,**params):
    """
    Fit the PCA.

    :param X: An H2OFrame; may contain NAs and/or categoricals.
    :param y: (Ignored)
    :param params: (Ignored)
    :return: This instance of H2OPCA.
    """
    self.pca_model = prcomp(x=X,k=self.parms["n_components"],
                            max_iterations=self.parms["max_iterations"],
                            seed=self.parms["seed"],
                            use_all_factor_levels=self.parms["use_all_factor_levels"],
                            pca_method=self.parms["pca_method"])
    return self

  def transform(self,X,y=None,**params):
    """
    Transform the given H2OFrame with the fitted PCA model.

    :param X: An H2OFrame; may contain NAs and/or categoricals.
    :param y: (Ignored)
    :param params: (Ignored)
    :return: An H2OFrame.
    """
    return self.pca_model.predict(X)