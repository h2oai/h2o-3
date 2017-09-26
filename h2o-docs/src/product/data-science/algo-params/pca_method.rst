``pca_method``
--------------

- Available in: PCA
- Hyperparameter: no

Description
~~~~~~~~~~~

Use the ``pca_method`` parameter to specify the algorithm to use for computing the principal components. Available options include:

   -  **GramSVD**: Uses a distributed computation of the Gram matrix, followed by a local SVD using the JAMA package
   -  **Power**: Computes the SVD using the power iteration method (experimental)
   -  **Randomized**: Uses randomized subspace iteration method
   -  **GLRM**: Fits a generalized low-rank model with L2 loss function and no regularization and solves for the SVD using local matrix algebra (experimental)

**Note**: For ``pca_method = Randomized``, the algorithm must deal with matrices of size *m* by *k* and *n* by *k*, where

   - *m* is number of rows,
   - *n* is expanded column size and
   - *k* is the number of eigenvectors desired.

As a result, there is no advantage to be gained by trying to find the eigenvectors of the matrix transpose. In other words, when using PCA with wide datasets, users should not choose Randomize method.

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

    # Train using the Power pca_method
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

    # Train again using GLRM pca_method
    birds2.pca <- h2o.prcomp(training_frame = birds.hex, transform = "STANDARDIZE",
                             k = 3, pca_method="GLRM", use_all_factor_levels=TRUE, 
                             impute_missing=TRUE)

    # View the importance of components
    birds2.pca@model$importance
    Importance of components: 
                                pc1      pc2      pc3
    Standard deviation     2.659459 0.700971 0.404706
    Proportion of Variance 0.915223 0.063583 0.021194
    Cumulative Proportion  0.915223 0.978806 1.000000

    # View the eigenvectors
    birds2.pca@model$eigenvectors
    Rotation: 
                      pc1      pc2       pc3
    patch.Ref1a -0.092008 0.030110 -0.018916
    patch.Ref1b -0.107461 0.040519  0.076546
    patch.Ref1c -0.103785 0.059700  0.016164
    patch.Ref1d -0.105764 0.044823  0.062234
    patch.Ref1e -0.102115 0.058994 -0.037536

    ---
                   pc1       pc2       pc3
    S         0.003558  0.111264 -0.422437
    year      0.000008 -0.004418  0.032813
    area      0.004551  0.049496 -0.444745
    log.area. 0.002756  0.066183 -0.453866
    ENN       0.013259 -0.274711 -0.053960
    log.ENN.  0.009517 -0.282830 -0.107461

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

    # Train again with the GLRM pca_method
    birds2 = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")
    birds2.pca = H2OPCA(k = 3, transform = "STANDARDIZE", 
                        pca_method="GLRM", use_all_factor_levels=True, 
                        impute_missing=True)
    birds2.pca.train(x=list(range(4)), training_frame=birds2)

    # View the importance of components
    birds2.pca.varimp(use_pandas=False)
    [(u'Standard deviation', 1.9286830840160667, 0.2896650415698226, 0.2053712844270903), 
    (u'Proportion of Variance', 0.9672162180423401, 0.021816948059531167, 0.01096683389812861), 
    (u'Cumulative Proportion', 0.9672162180423401, 0.9890331661018713, 0.9999999999999999)]

    # View the eigenvectors
    birds2.pca.rotation()
    Rotation: 
                       pc1                pc2                pc3
    -----------------  -----------------  -----------------  -----------------
    patch.Ref1a        -0.0973454860413    0.0233748845619   -0.0407839669099
    patch.Ref1b        -0.0979880717715    -0.0167446302072  -0.0162149496631
    patch.Ref1c        -0.0971529563124    0.00536661170128  -0.0177009628488
    patch.Ref1d        -0.100657197505     0.00754923938494  -0.018364320893
    patch.Ref1e        -0.0982933822825    0.0158116058361   -0.0193764027317
    ---                ---                 ---               ---
    landscape.Bauxite  -0.0248166745792    -0.504864083913   0.074374750806
    landscape.Forest   -0.0296555294277    0.232678445269    -0.537738667852
    landscape.Urban    -0.0733909967344    -0.112998988851   0.0347355699687
    S                  0.00878461186804    0.649068763107    -0.130282514102
    year               -0.000583301909773  -0.0765116904321  -0.69416666169

    See the whole table with table.as_data_frame()
