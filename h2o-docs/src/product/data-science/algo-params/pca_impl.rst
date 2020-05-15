``pca_impl``
--------------

- Available in: PCA
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``pca_impl`` parameter allows you to specify PCA implementations for Singular-Value Decomposition (SVD) or Eigenvalue Decomposition (EVD), using either the Matrix Toolkit Java (`MTJ <https://github.com/fommil/matrix-toolkits-java/>`__) libary or the Java Matrix (`JAMA <http://math.nist.gov/javanumerics/jama/>`__) library.

Available options include:

- **mtj_evd_densematrix**: Eigenvalue decompositions for dense matrix using MTJ
- **mtj_evd_symmmatrix**: Eigenvalue decompositions for symmetric matrix using MTJ (default)
- **mtj_svd_densematrix**: Singular-value decompositions for dense matrix using MTJ
- **jama**: Eigenvalue decompositions for dense matrix using JAMA

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Load the US Arrests dataset
        arrests = h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

        # Train using the JAMA PCA implementation option
        model <- h2o.prcomp(training_frame = arrests, k = 4, pca_impl = "JAMA", seed = 1234)

        # View the importance of components
        model@model$importance
        Importance of components: 
                                      pc1       pc2      pc3      pc4
        Standard deviation     202.723056 27.832264 6.523048 2.581365
        Proportion of Variance   0.980347  0.018479 0.001015 0.000159
        Cumulative Proportion    0.980347  0.998826 0.999841 1.000000

        # View the eigenvectors
        model@model$eigenvectors
        Rotation: 
                       pc1       pc2       pc3       pc4
        Murder   -0.042392 -0.016163  0.065884  0.996795
        Assault  -0.943957 -0.320686 -0.066552 -0.040946
        UrbanPop -0.308428  0.938459 -0.154967  0.012343
        Rape     -0.109637  0.127257  0.983471 -0.067603

   .. code-tab:: python

        import(h2o)
        h2o.init()
        from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA

        # Load the US Arrests dataset
        arrestsH2O = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

        # Train using the jama PCA implementation option
        impl = "jama"
        model = H2OPCA(k = 4, pca_impl=impl, seed=1234)
        model.train(x=list(range(4)), training_frame=arrestsH2O)

        # View the importance of components
        model.varimp(use_pandas=False)
        [(u'Standard deviation', 202.7230564425318, 27.832263730019577, 6.523048232982174, 2.5813652317810947), 
         (u'Proportion of Variance', 0.980347353161874, 0.0184786717900806, 0.0010150206303792286, 0.00015895441766549314), 
         (u'Cumulative Proportion', 0.980347353161874, 0.9988260249519546, 0.9998410455823339, 0.9999999999999993)]

        # View the eigenvectors
        model.rotation()
        Rotation: 
                  pc1         pc2         pc3         pc4
        --------  ----------  ----------  ----------  ----------
        Murder    -0.0423918  -0.0161626  0.0658843   0.996795
        Assault   -0.943957   -0.320686   -0.0665517  -0.0409457
        UrbanPop  -0.308428   0.938459    -0.154967   0.0123426
        Rape      -0.109637   0.127257    0.983471    -0.0676028
