``use_all_factor_levels``
-------------------------

- Available in: Deep Learning, PCA
- Hyperparameter: no

Description
~~~~~~~~~~~

This option allows you to specify whether to use all factor levels in the possible set of predictors. This option is disabled by default, so the first factor level is skipped. If you enable this option, then the PCA model ignores the first factor level of each categorical column when expanding into indicator columns. Note also that if you enable this option, then sufficient regularization is required. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

    # Load the Birds dataset
    birds.hex <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Train using all factor levels
    birds.pca <- h2o.prcomp(training_frame = birds.hex, transform = "STANDARDIZE",
                            k = 3, pca_method="Power", use_all_factor_levels=TRUE)

    # View the importance of components
    birds.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     1.546397 1.348276 1.055239
    Proportion of Variance 0.300269 0.228258 0.139820
    Cumulative Proportion  0.300269 0.528527 0.668347

    # View the eigenvectors
    birds.pca@model$eigenvectors
    Rotation: 
                      pc1      pc2       pc3
    patch.Ref1a  0.009848 -0.005947 0.001061
    patch.Ref1b -0.001628 -0.014739 0.001007
    patch.Ref1c  0.004994 -0.009486 0.000523
    patch.Ref1d  0.000117 -0.004400 0.004917
    patch.Ref1e  0.003627 -0.001467 0.004268

    ---
                    pc1       pc2       pc3
    S          0.515048  0.226915  0.123136
    year      -0.066269 -0.069526 -0.971250
    area       0.414050  0.344332 -0.149339
    log.area.  0.497313  0.363609 -0.131261
    ENN       -0.390235  0.545631  0.007944
    log.ENN.  -0.345665  0.562834  0.002092

    # Train again without using all factor levels
    birds2.pca <- h2o.prcomp(training_frame = birds.hex, transform = "STANDARDIZE",
                             k = 3, pca_method="Power", use_all_factor_levels=FALSE)

    # View the importance of components
    birds2.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     1.544463 1.342094 1.054848
    Proportion of Variance 0.309387 0.233622 0.144320
    Cumulative Proportion  0.309387 0.543008 0.687328

    # View the eigenvectors
    birds2.pca@model$eigenvectors
    Rotation: 
                      pc1      pc2       pc3
    patch.Ref1b -0.001469 0.014976  0.000849
    patch.Ref1c  0.005120 0.009480  0.000457
    patch.Ref1d  0.000164 0.004468  0.004877
    patch.Ref1e  0.003656 0.001399  0.004283
    patch.Ref1g  0.005728 0.002821 -0.003653

    ---
                    pc1       pc2       pc3
    S          0.510775 -0.233390  0.123700
    year      -0.064706  0.068396 -0.973014
    area       0.409889 -0.355035 -0.145441
    log.area.  0.494189 -0.379361 -0.125400
    ENN       -0.397489 -0.543776  0.012354
    log.ENN.  -0.355681 -0.554631  0.002802

   .. code-block:: python

    import(h2o)
    h2o.init()
    from h2o.transforms.decomposition import H2OPCA

    # Load the Birds dataset
    birds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Train using all factor levels
    birds.pca = H2OPCA(k = 3, transform = "STANDARDIZE", pca_method="Power", 
                       use_all_factor_levels=True)
    birds.pca.train(x=list(range(4)), training_frame=birds)

    # View the importance of components
    birds.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 1.123848642024252, 0.9495543060913556, 0.5348966295982289), 
    (u'Proportion of Variance', 0.30806239666469637, 0.21991895069672493, 0.06978510918460921), 
    (u'Cumulative Proportion', 0.30806239666469637, 0.5279813473614213, 0.5977664565460306)]

    # View the eigenvectors
    birds.pca.rotation()
    Rotation: 
                       pc1                 pc2                pc3
    -----------------  ------------------  -----------------  ----------------
    patch.Ref1a        0.00898674959389   -0.0133755203032    -0.0386887320947
    patch.Ref1b        -0.00583910658193  0.0085085283222     -0.0403921689887
    patch.Ref1c        0.00157382150598   -0.0024334959905    -0.0395404505417
    patch.Ref1d        0.00205431395425   0.00464763109547    -0.0130225732894
    patch.Ref1e        0.00521157104674   -9.98807074937e-07  -0.0126676561766
    ---                ---                ---                 ---
    landscape.Bauxite  -0.092706414975    0.0985077063774     -0.312254873011
    landscape.Forest   0.0498033442402    -0.0606680332043    -0.928822711491
    landscape.Urban    -0.0671561311604   0.108679950954      -0.0336397179284
    S                  0.661206197437     -0.694121601584     0.0166591597288
    year               -0.727793158751    -0.684904471511     0.00409291352783

    See the whole table with table.as_data_frame()

    # Train again without using all factor levels
    birds2 = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")
    birds2.pca = H2OPCA(k = 3, transform = "STANDARDIZE", pca_method="Power", 
                        use_all_factor_levels=False) 
    birds2.pca.train(x=list(range(4)), training_frame=birds2)

    # View the importance of components
    birds2.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 1.1172889937645427, 0.9428301355878612, 0.5343711223812785), 
    (u'Proportion of Variance', 0.3239196034161728, 0.2306604322634375, 0.07409555444280075), 
    (u'Cumulative Proportion', 0.3239196034161728, 0.5545800356796103, 0.628675590122411)]

    # View the eigenvectors
    birds2.pca.rotation()
    Rotation: 
                       pc1                pc2                pc3
    -----------------  -----------------  -----------------  -----------------
    patch.Ref1b        0.00573715248567   0.00905029823292   0.0397305412063
    patch.Ref1c        -0.00155941141753  -0.00262429190783  0.0388265166788
    patch.Ref1d        -0.00220082271557  0.00460340227135   0.0127992097357
    patch.Ref1e        -0.00530828965991  -0.00035582622718  0.0124225177099
    patch.Ref1g        0.00398590526959   0.00628351783691   0.0261357246393
    ---                ---                ---                ---
    landscape.Bauxite  0.0926709193464    0.108265715468     0.368430097989
    landscape.Forest   -0.049531997119    -0.0658907199023   0.910420643338
    landscape.Urban    0.0662724833811    0.116520039037     0.0360237860344
    S                  -0.643180719366    -0.730003524026    -0.0176460246561
    year               0.753676017614     -0.65628159817     -0.00410087043089

    See the whole table with table.as_data_frame()
