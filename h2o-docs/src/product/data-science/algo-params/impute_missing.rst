``impute_missing``
------------------

- Available in: PCA
- Hyperparameter: no

Description
~~~~~~~~~~~

In some cases, dataset used can contain a fewer number of rows due to the removal of rows with NA/missing values. If this is not the desired behavior, then you can use the ``impute_missing`` option to impute missing entries in each column with the column mean value. 

This value defaults to False.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # Load the Birds dataset
    birds.hex <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Train with impute_missing enabled
    birds.pca <- h2o.prcomp(training_frame = birds.hex, transform = "STANDARDIZE",
                            k = 3, pca_method="Power", use_all_factor_levels=TRUE, 
                            impute_missing=TRUE)

    # View the importance of components
    birds.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     1.496991 1.351000 1.014182
    Proportion of Variance 0.289987 0.236184 0.133098
    Cumulative Proportion  0.289987 0.526171 0.659269

    # View the eigenvectors
    birds.pca@model$eigenvectors
    Rotation: 
                      pc1      pc2       pc3
    patch.Ref1a  0.007207 0.007449  0.001161
    patch.Ref1b -0.003090 0.011257 -0.001066
    patch.Ref1c  0.002962 0.008850 -0.000264
    patch.Ref1d -0.001295 0.011003  0.000501
    patch.Ref1e  0.006559 0.006904 -0.001206

    ---
                    pc1       pc2       pc3
    S          0.463591 -0.053410  0.184799
    year      -0.055934  0.009691 -0.968635
    area       0.533375 -0.289381 -0.130338
    log.area.  0.583966 -0.262287 -0.089582
    ENN       -0.270615 -0.573900  0.038835
    log.ENN.  -0.231368 -0.640231  0.026325

    # Train again without imputing missing values
    birds2.pca <- h2o.prcomp(training_frame = birds.hex, transform = "STANDARDIZE",
                             k = 3, pca_method="Power", use_all_factor_levels=TRUE, 
                             impute_missing=FALSE)

    Warning message:
    In doTryCatch(return(expr), name, parentenv, handler) :
      _train: Dataset used may contain fewer number of rows due to removal of rows 
      with NA/missing values. If this is not desirable, set impute_missing argument 
      in pca call to TRUE/True/true/... depending on the client language.

    # View the importance of components
    birds2.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     1.546397 1.348276 1.055239
    Proportion of Variance 0.300269 0.228258 0.139820
    Cumulative Proportion  0.300269 0.528527 0.668347

    # View the eigenvectors
    birds2.pca@model$eigenvectors
    Rotation: 
                      pc1       pc2       pc3
    patch.Ref1a  0.009848 -0.005947 -0.001061
    patch.Ref1b -0.001628 -0.014739 -0.001007
    patch.Ref1c  0.004994 -0.009486 -0.000523
    patch.Ref1d  0.000117 -0.004400 -0.004917
    patch.Ref1e  0.003627 -0.001467 -0.004268

    ---
                    pc1       pc2       pc3
    S          0.515048  0.226915 -0.123136
    year      -0.066269 -0.069526  0.971250
    area       0.414050  0.344332  0.149339
    log.area.  0.497313  0.363609  0.131261
    ENN       -0.390235  0.545631 -0.007944
    log.ENN.  -0.345665  0.562834 -0.002092

   .. code-block:: python

    import(h2o)
    h2o.init()
    from h2o.transforms.decomposition import H2OPCA

    # Load the Birds dataset
    birds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Train with impute_missing enabled
    birds.pca = H2OPCA(k = 3, transform = "STANDARDIZE", pca_method="Power", 
                       use_all_factor_levels=True, impute_missing=True)
    birds.pca.train(x=list(range(4)), training_frame=birds)

    # View the importance of components
    birds.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 1.0505993078459912, 0.8950182545325247, 0.5587566783073901), 
    (u'Proportion of Variance', 0.28699613488673914, 0.20828865401845226, 0.08117966990084355), 
    (u'Cumulative Proportion', 0.28699613488673914, 0.4952847889051914, 0.5764644588060349)]

    # View the eigenvectors
    birds.pca.rotation()
    Rotation: 
                       pc1                 pc2                pc3
    -----------------  ------------------  -----------------  ----------------
    patch.Ref1a        0.00732398141913    -0.0141576160836   0.0294419461081
    patch.Ref1b        -0.00482860843905   0.00867426840498   0.0330778190153
    patch.Ref1c        0.00124768649004    -0.00274167383932  0.0312598825617
    patch.Ref1d        -0.000370181920761  0.000297923901103  0.0317439245635
    patch.Ref1e        0.00223394447742    -0.00459462277502  0.0309648089406
    ---                ---                 ---                ---
    landscape.Bauxite  -0.0638494513759    0.136728811833     0.118858152002
    landscape.Forest   0.0378085502606     -0.0833578672691   0.969316569884
    landscape.Urban    -0.0545759062856    0.111309410422     0.0354475756223
    S                  0.564501605704      -0.767095710638    -0.0466832766991
    year               -0.814596906726     -0.577331674836    -0.0101626722479

    See the whole table with table.as_data_frame()

    # Train again without imputing missing values
    birds2 = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")
    birds2.pca = H2OPCA(k = 3, transform = "STANDARDIZE", 
                        pca_method="Power", use_all_factor_levels=True, 
                        impute_missing=False)
    birds2.pca.train(x=list(range(4)), training_frame=birds2)

    # View the importance of components
    birds2.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 1.1238486420242524, 0.949554306091356, 0.534896629598228), 
    (u'Proportion of Variance', 0.3080623966646966, 0.21991895069672512, 0.06978510918460899), 
    (u'Cumulative Proportion', 0.3080623966646966, 0.5279813473614217, 0.5977664565460307)]

    # View the eigenvectors
    birds2.pca.rotation()
    Rotation: 
                       pc1                pc2                pc3
    -----------------  -----------------  -----------------  -----------------
    patch.Ref1a        0.00898674970716   0.0133755203176    0.0386887315027
    patch.Ref1b        -0.00583910665399  -0.00850852817775  0.0403921679996
    patch.Ref1c        0.00157382152659   0.00243349606991   0.0395404497512
    patch.Ref1d        0.00205431391489   -0.00464763108225  0.0130225730145
    patch.Ref1e        0.00521157104675   9.98792622547e-07  0.0126676559841
    ---                ---                ---                ---
    landscape.Bauxite  -0.0927064158093   -0.0985077050027   0.312254932996
    landscape.Forest   0.049803344754     0.0606680349608    0.928822693132
    landscape.Urban    -0.0671561320808   -0.108679950396    0.033639706807
    S                  0.661206203315     0.69412159594      -0.0166591571667
    year               -0.727793152951    0.684904477663     -0.00409291536614

    See the whole table with table.as_data_frame()

