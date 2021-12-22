from h2o.frame import H2OFrame
from ..estimators import H2OPrincipalComponentAnalysisEstimator, H2OSingularValueDecompositionEstimator
from ..model import ModelBase
from .transform_base import H2OTransformer


class _H2OTransformerProxy(H2OTransformer, ModelBase):
    """
    The order or base classes is important here as we don't want to inherit the instance properties of ModelBase.
    """

    def __init__(self, delegate, allowed_params):
        super(_H2OTransformerProxy, self).__init__()
        self._delegate = delegate
        self._allowed_params = allowed_params

    def __getattr__(self, key):
        return getattr(self._delegate, key)

    def __setattr__(self, key, value):
        if key in ['_delegate', '_allowed_params']:
            super(_H2OTransformerProxy, self).__setattr__(key, value)
        else:
            setattr(self._delegate, key, value)
        
    def get_params(self, deep=True):
        return {k: v for k, v in self._delegate.get_params().items() if k in self._allowed_params}

    def set_params(self, **params):
        return self._delegate.set_params(**params)

    def fit(self, X, y=None, **params):
        self._delegate.fit(X)
        return self

    def transform(self, X, y=None, **params):
        """
        Transform the given H2OFrame using the fitted model.

        :param H2OFrame X: May contain NAs and/or categorical data.
        :param H2OFrame y: Ignored by transformers. Should be None.
        :param params: Ignored.

        :returns: The transformed H2OFrame.
        """
        return self._delegate.predict(X)


class H2OPCA(_H2OTransformerProxy):
    """ Principal Component Analysis """
    
    def __init__(self, 
                 model_id=None,
                 k=None,
                 max_iterations=None,
                 seed=None,
                 transform="none",
                 use_all_factor_levels=False,
                 pca_method="gram_svd",
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

            - ``"none"``: for no transformation,
            - ``"demean"``: for subtracting the mean of each column,
            - ``"descale"``: for dividing by the standard deviation of each column,
            - ``"standardize"``: for demeaning and descaling, and
            - ``"normalize"``: for demeaning and dividing each column by its range (max - min).

        :param int seed: Random seed used to initialize the right singular vectors at the beginning of each
            power method iteration.
        :param int max_iterations: The maximum number of iterations when pca_method is "Power".
        :param bool use_all_factor_levels: A logical value indicating whether all factor levels should be included
            in each categorical column expansion. If False, the indicator column corresponding to the first factor
            level of every categorical variable will be dropped. Default is False.
        :param str pca_method: A character string that indicates how PCA should be calculated. Possible values are:

            - ``"gram_svd"``: distributed computation of the Gram matrix followed by a local SVD using the JAMA package,
            - ``"power"``: computation of the SVD using the power iteration method,
            - ``"glrm"``: fit a generalized low rank model with an l2 loss function (no regularization) and solve for
              the SVD using local matrix algebra.
            - ``"randomized"``: computation of the SVD using the randomized method from thesis of Nathan P. Halko,
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
        :param bool compute_metrics: whether to compute metrics on training data.  Default is True.

        :returns: A new instance of H2OPCA.

        """
        allowed_params = locals().keys()
        super(H2OPCA, self).__init__(
            delegate=H2OPrincipalComponentAnalysisEstimator(
                model_id=model_id,
                k=k,
                max_iterations=max_iterations,
                seed=seed,
                transform=transform,
                use_all_factor_levels=use_all_factor_levels,
                pca_method=pca_method,
                pca_impl=pca_impl,
                ignore_const_cols=ignore_const_cols,
                impute_missing=impute_missing,
                compute_metrics=compute_metrics
            ), 
            allowed_params=allowed_params
        )


class H2OSVD(_H2OTransformerProxy):
    """ Singular Value Decomposition """

    def __init__(self, 
                 nv=None, 
                 max_iterations=None, 
                 transform="none", 
                 seed=None,
                 use_all_factor_levels=None, 
                 svd_method="gram_svd"):
        """
        Singular value decomposition of an H2OFrame.

        :param int nv: The number of right singular vectors to be computed. This must be between 1 and
            min(ncol(training_frame), snrow(training_frame)) inclusive.
        :param int max_iterations: The maximum number of iterations to run each power iteration loop. Must be
            between 1 and 1e6 inclusive.
        :param str transform: A character string that indicates how the training data should be transformed
            before running SVD. Possible values are:

            - ``"none"``: for no transformation,
            - ``"demean"``: for subtracting the mean of each column,
            - ``"descale"``: for dividing by the standard deviation of each column,
            - ``"standardize"``: for demeaning and descaling, and
            - ``"normalize"``: for demeaning and dividing each column by its range (max - min).

        :param int seed: Random seed used to initialize the right singular vectors at the beginning of each
            power method iteration.
        :param bool use_all_factor_levels: A logical value indicating whether all factor levels should be included
            in each categorical column expansion. If False, the indicator column corresponding to the first factor
            level of every categorical variable will be dropped. Defaults to True.
        :param str svd_method: A character string that indicates how SVD should be calculated. Possible values are:

            - ``"gram_svd"``: distributed computation of the Gram matrix followed by a local SVD
              using the JAMA package,
            - ``"power"``: computation of the SVD using the power iteration method,
            - ``"randomized"``: approximate SVD by projecting onto a random subspace.

        :returns: a new H2OSVD model
        """
        allowed_params = locals().keys()
        super(H2OSVD, self).__init__(
            delegate=H2OSingularValueDecompositionEstimator(
                nv=nv,
                max_iterations=max_iterations,
                transform=transform,
                seed=seed,
                use_all_factor_levels=use_all_factor_levels,
                svd_method=svd_method
            ),
            allowed_params=allowed_params
        )
