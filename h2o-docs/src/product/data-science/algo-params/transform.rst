``transform``
-------------

- Available in: PCA
- Hyperparameter: yes

Description
~~~~~~~~~~~

Use the ``transform`` parameter to specify the transformation method used for the training data. Available options include:

- None (default): Do not perform any transformations on the data. 
- Standardize: Standardizing subtracts the mean and then divides each variable by its standard deviation.
- Normalize: Scales all numeric variables in the range [0,1]. 
- Demean: The mean for each variable is subtracting from each observation resulting in mean zero.  Note that it is not always advisable to demean the data if the Moving Average parameter is of primary interest to estimate.
- Descale: Divides by the standard deviation of each column.

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

    # Train using Standardized transform
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

    # Train again using Normalize transform
    birds2.pca <- h2o.prcomp(training_frame = birds.hex, transform = "NORMALIZE",
                             k = 3, pca_method="Power", use_all_factor_levels=TRUE, 
                             impute_missing=TRUE)

    # View the importance of components
    birds2.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     0.632015 0.531616 0.517096
    Proportion of Variance 0.166444 0.117764 0.111418
    Cumulative Proportion  0.166444 0.284208 0.395626

    # View the eigenvectors
    birds2.pca@model$eigenvectors
    Rotation: 
                    pc1       pc2       pc3
    patch.Ref1a 0.026631 -0.006839 0.008674
    patch.Ref1b 0.025825 -0.010199 0.004386
    patch.Ref1c 0.026240 -0.008322 0.006759
    patch.Ref1d 0.026106 -0.009375 0.005472
    patch.Ref1e 0.026313 -0.007510 0.007769

    ---
                    pc1       pc2       pc3
    S          0.055295  0.113531  0.141168
    year      -0.003343 -0.013812 -0.019785
    area      -0.011008  0.064146  0.087213
    log.area.  0.007378  0.080143  0.086986
    ENN       -0.151652 -0.026572 -0.013064
    log.ENN.  -0.463210 -0.046953  0.086169

   .. code-block:: python

    import(h2o)
    h2o.init()
    from h2o.transforms.decomposition import H2OPCA

    # Load the Birds dataset
    birds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Train with the Power pca_method
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

    # Train again using Normalize transform
    birds2 = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")
    birds2.pca = H2OPCA(k = 3, transform = "NORMALIZE", pca_method="Power", 
                        use_all_factor_levels=True, impute_missing=True)
    birds2.pca.train(x=list(range(4)), training_frame=birds2)

    # View the importance of components
    birds2.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 0.5615959368803389, 0.527199563812311, 0.5094397597133178), 
    (u'Proportion of Variance', 0.14220176282406302, 0.12531618081504411, 0.11701532412044723), 
    (u'Cumulative Proportion', 0.14220176282406302, 0.26751794363910714, 0.3845332677595544)]

    # View the eigenvectors
    birds2.pca.rotation()
    Rotation: 
                       pc1                pc2                pc3
    -----------------  -----------------  -----------------  -----------------
    patch.Ref1a        0.0321402336467    -5.67047495074e-05  0.000466136314122 
    patch.Ref1b        0.0312293374798    -0.00233972080607   -0.00219708018283
    patch.Ref1c        0.0316847855632    -0.00119821277779   -0.000865471934357
    patch.Ref1d        0.0315635183971    -0.00150214960133   -0.00122002465866
    patch.Ref1e        0.0317587104328    -0.00101293187492   -0.000649335409312
    ---                ---                ---                 ---
    landscape.Bauxite  -0.0276965008223   -0.962683908867     0.166590998707
    landscape.Forest   0.982163161865     -0.0373079859488    -0.0270202298116
    landscape.Urban    -0.00873355942469  -0.0280626855484    -0.0394249459161
    S                  0.0515403663478    0.113344870593      0.123141154399
    year               -0.00488342003667  -0.0143717060558    -0.0187277019153

    See the whole table with table.as_data_frame()

