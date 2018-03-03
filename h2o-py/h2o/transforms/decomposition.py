from ..estimators.estimator_base import H2OEstimator
from h2o.utils.typechecks import Enum
from h2o.utils.typechecks import assert_is_type


class H2OPCA(H2OEstimator):
    """
    Principal Component Analysis
    """
    algo = "pca"

    def __init__(self, model_id=None, k=None, max_iterations=None, seed=None,
                 transform="NONE",
                 use_all_factor_levels=False,
                 pca_method="GramSVD",
                 pca_impl="mtj_evd_symmmatrix",
                 ignore_const_cols=True,
                 impute_missing=False,
                 compute_metrics=True):
        """
        Principal Components Analysis

        :param str model_id: The unique hex key assigned to the resulting model. Automatically generated if
            none is provided.
        :param int k: The number of principal components to be computed. This must be between ``1`` and
            ``min(ncol(training_frame), nrow(training_frame))`` inclusive.
        :param str transform: A character string that indicates how the training data should be transformed
            before running PCA. Possible values are:

            - ``"NONE"``: for no transformation,
            - ``"DEMEAN"``: for subtracting the mean of each column,
            - ``"DESCALE"``: for dividing by the standard deviation of each column,
            - ``"STANDARDIZE"``: for demeaning and descaling, and
            - ``"NORMALIZE"``: for demeaning and dividing each column by its range (max - min).

        :param int seed: Random seed used to initialize the right singular vectors at the beginning of each
            power method iteration.
        :param int max_iterations: The maximum number of iterations when pca_method is "Power".
        :param bool use_all_factor_levels: A logical value indicating whether all factor levels should be included
            in each categorical column expansion. If False, the indicator column corresponding to the first factor
            level of every categorical variable will be dropped. Default is False.
        :param str pca_method: A character string that indicates how PCA should be calculated. Possible values are:

            - ``"GramSVD"``: distributed computation of the Gram matrix followed by a local SVD using the JAMA package,
            - ``"Power"``: computation of the SVD using the power iteration method,
            - ``"GLRM"``: fit a generalized low rank model with an l2 loss function (no regularization) and solve for
              the SVD using local matrix algebra.
            - ``"Randomized"``: computation of the SVD using the randomized method from thesis of Nathan P. Halko,
                Randomized methods for computing low-rank approximation of matrices.
        :param str pca_impl: A character string that indicates the implementation to use for
            computing PCA (via SVD or EVD).

            - ``"mtj_evd_densematrix"``: eigenvalue decompositions for dense matrix using MTJ
            - ``"mtj_evd_symmmatrix"``: eigenvalue decompositions for symmetric matrix using MTJ
            - ``"mtj_svd_densematrix"``: singular-value decompositions for dense matrix using MTJ
            - ``"jama"``: eigenvalue decompositions for dense matrix using JAMA

              References:
              - JAMA: http://math.nist.gov/javanumerics/jama/
              - MTJ: https://github.com/fommil/matrix-toolkits-java/

            One of the following implementations are available: ``"mtj_evd_densematrix"``,
            ``"mtj_evd_symmmatrix"``, ``"mtj_svd_densematrix"``, ``"jama"``  (default: ``"mtj_evd_symmmatrix"``).
        :param bool ignore_const_cols: If true, will ignore constant columns.  Default is True.
        :param bool impute_missing:  whether to impute NA/missing values.
        :param bool compute_metrics: whether to compute metrics on training data.  Default to True

        :returns: A new instance of H2OPCA.

        """
        super(H2OPCA, self).__init__()
        self._parms = locals()
        self._parms = {k: v for k, v in self._parms.items() if k != "self"}

        assert_is_type(pca_method, Enum("GramSVD", "Power", "GLRM", "Randomized"))
        self._parms["pca_method"] = pca_method
        assert_is_type(pca_impl, Enum("MTJ_EVD_DENSEMATRIX", "MTJ_EVD_SYMMMATRIX", "MTJ_SVD_DENSEMATRIX", "JAMA"))
        self._parms["pca_impl"] = pca_impl
        assert_is_type(transform, Enum("NONE", "DEMEAN", "DESCALE", "STANDARDIZE", "NORMALIZE"))
        self._parms["transform"] = transform

    def fit(self, X, y=None, **params):
        return super(H2OPCA, self).fit(X)


    def transform(self, X, y=None, **params):
        """
        Transform the given H2OFrame with the fitted PCA model.

        :param H2OFrame X: May contain NAs and/or categorical data.
        :param H2OFrame y: Ignored for PCA. Should be None.
        :param params: Ignored.

        :returns: The input H2OFrame transformed by the Principal Components.
        """
        return self.predict(X)

class H2OSVD(H2OEstimator):
    """Singular Value Decomposition"""
    algo = "svd"

    def __init__(self, nv=None, max_iterations=None, transform=None, seed=None,
                 use_all_factor_levels=None, svd_method="GramSVD"):
        """
        Singular value decomposition of an H2OFrame.

        :param int nv: The number of right singular vectors to be computed. This must be between 1 and
            min(ncol(training_frame), snrow(training_frame)) inclusive.
        :param int max_iterations: The maximum number of iterations to run each power iteration loop. Must be
            between 1 and 1e6 inclusive.
        :param str transform: A character string that indicates how the training data should be transformed
            before running SVD. Possible values are:

            - ``"NONE"``: for no transformation,
            - ``"DEMEAN"``: for subtracting the mean of each column,
            - ``"DESCALE"``: for dividing by the standard deviation of each column,
            - ``"STANDARDIZE"``: for demeaning and descaling, and
            - ``"NORMALIZE"``: for demeaning and dividing each column by its range (max - min).

        :param int seed: Random seed used to initialize the right singular vectors at the beginning of each
            power method iteration.
        :param bool use_all_factor_levels: A logical value indicating whether all factor levels should be included
            in each categorical column expansion. If False, the indicator column corresponding to the first factor
            level of every categorical variable will be dropped. Defaults to True.
        :param str svd_method: A character string that indicates how SVD should be calculated. Possible values are:

            - ``"GramSVD"``: distributed computation of the Gram matrix followed by a local SVD
              using the JAMA package,
            - ``"Power"``: computation of the SVD using the power iteration method,
            - ``"Randomized"``: approximate SVD by projecting onto a random subspace.

        :returns: a new H2OSVD model
        """
        super(H2OSVD, self).__init__()
        self._parms = locals()
        self._parms = {k: v for k, v in self._parms.items() if k != "self"}

        assert_is_type(svd_method, Enum("GramSVD", "Power", "GLRM", "Randomized"))
        self._parms["svd_method"] = svd_method
        assert_is_type(transform, Enum("NONE", "DEMEAN", "DESCALE", "STANDARDIZE", "NORMALIZE"))
        self._parms["transform"]=transform
        self._parms['_rest_version'] = 99


    def fit(self, X, y=None, **params):
        return super(H2OSVD, self).fit(X)


    def transform(self, X, y=None, **params):
        """
        Transform the given H2OFrame with the fitted SVD model.

        :param H2OFrame X: May contain NAs and/or categorical data.
        :param H2OFrame y: Ignored for SVD. Should be None.
        :param params: Ignored.

        :returns: The input H2OFrame transformed by the SVD.
        """
        return self.predict(X)
