#Recent Changes   

##H2O

###Slotnick (3.4.0.1)

####New Features
The following changes represent features that have been added since the previous release:

#####API

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/8a2aefb72ab9c64c7064a65c242239a69cbf87a3): Added `NumList` and `StrList`
- [PUBDEV-674](https://0xdata.atlassian.net/browse/PUBDEV-674): Added REST API and R / Python for grid search

#####Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c18cfab41bd32fd4c2f34fdda5cf73076c1320f6): Added option in PCA to use randomized subspace iteration method for calculation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/acccdf7e0a698b2960aac8260c8284c6523d1fd5): Deep Learning: Added `target_ratio_comm_to_comp` to R and Python client APIs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a17dcc0637a04fc7e63c020bd0a3f2bba7b6f674): PUBDEV-1247: Added stochastic GBM parameters (`sample_rate` and `col_sample_rate`) to R/Py APIs
- [PUBDEV-1450](https://0xdata.atlassian.net/browse/PUBDEV-1450): GLRM has been tested and removed from "experimental" status

#####Hadoop

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ba2755313d22f3812742786269ababc72257a179): Added support for H2O with HDP2.3

#####Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cc02cc6f19360a79232a781328c6afae80a4861a): Added `_to_string` method 
- [PUBDEV-2166](https://0xdata.atlassian.net/browse/PUBDEV-2166): Added Python grid client [GitHub commit](https://github.com/h2oai/h2o-3/commit/16589b7d3362dce6a2caaed6e23287c605896a8a)
- [PUBDEV-2098](https://0xdata.atlassian.net/browse/PUBDEV-2098): Scoring history in Python is now visualized ([GitHub commit](https://github.com/h2oai/h2o-3/commit/77b27109c84c4739f9f1b7a3078f8992beefc813))
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3cda6e1a810dcbee5182dde5821a65f3b8800a69): PUBDEV-2020: Python implementation and test for `split_frame()`


#####R

>This software release introduces changes to the R API that may cause previously written R scripts to be inoperable. For more information, refer to the following [link](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/upgrade/RChanges.md). 

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a989234a0ec9d6ded30441a2c6d2672ef5731379): Added `h2o.getTypes()` to the R wrapper
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c630e40f5ba577e912aaf44d3c7f7fb10f1693dd): Added ability to set `col.types` with a named list 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/875418caebf8f12aca1675f124c2d5135670642a): Added `h2o.getId()` to get the back-end distributed key/value store ID from a Frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9420547451c5ef6dcb04b8803d0a400c720445a4): Added column types to H2O frame in R, which allows R to set the correct column types when `as.data.frame()` is used on an H2O frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e08405307b6499dbf09d264ab2ee8798b496a5d): Added `@export` for exported R functions

#####System

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/de0b19c71a18f09eeace304773adebb51772e311): Added string length util for Enum columns
- [[GitHub commit](https://github.com/h2oai/h2o-3/commit/7b8e39e8a6624d2512620d9e230ff91dd9c7e240): Added pass-through version of `toCategoricalVec()`, `toNumericVec()`, and `toStringVec()` to `Vec.java` for code simplicity and backwards compatibility
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/20ccac7947232fbb68e318e013c0ac2a96870284): Added string column handling to `StrSplit()`

#####Web UI

- [PUBDEV-1977](https://0xdata.atlassian.net/browse/PUBDEV-1977): Added grid search to Flow web UI



####Enhancements

The following changes are improvements to existing features (which includes changed default values):

#####Algorithms

- [PUBDEV-467](https://0xdata.atlassian.net/browse/PUBDEV-467): Show Frames for DL weights/biases in Flow
- [PUBDEV-1847](https://0xdata.atlassian.net/browse/PUBDEV-1847): DRF/GBM: `nbins_top_level` is now configurable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f9b1fea92c46105d0a2a54874eb7898993e6f718): Deep Learning: Scoring time is now shown in the logs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ad041d3b5ff96ed33ea22692035f02c21b461a68): Sped up GBM split finding by dynamically switching between single and multi-threaded based on workload
- [PUBDEV-1247](https://0xdata.atlassian.net/browse/PUBDEV-1247): Implemented Stochastic GBM
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5ada6c5f654e75c2275e1fc5027a306c44793ea3): Parallelized split finding for GBM/DRF (useful for large numbers of columns and nbins).
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/66230ffcf7276c83aa8db52cb9656efba06ec45a): Added improvements to speed up DRF (up to 35% faster) and stochastic GBM (up to 5x faster)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f48b52cf0a8f74a57c60a9cafd979ff28cd4a4c0): Added some straight-forward optimizations for GBM histogram building
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5ccc4699f3c71dccb64f7c11fac5a91ddff514ba): GLRM is now deterministic between one vs. many chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ae79aec8a84d0b7bdabb60f15e8138218e5e227e): Input parameters are now immutable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c6ad99d06a337f7535720852213e4d55fa116e8a): PUBDEV-2135: Cleaned up N-fold CV model parameter sanity checking and error message propagation; now checks all N-fold model parameters upfront and lets the main model carry the message to the user
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/756ed15a8a8e34e4383cba1a6580c24806603c49): PUBDEV-2130: N-fold CV models are no longer deleted when the main model is deleted
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c7339d0597f690aef491b343797368c27645bb64): PUBDEV-2107: The title in `plot.H2OBinomialMetrics` is now editable 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/61d125c4b1a457fc95c5daf2a2423b3934a1d6eb): Parse Python lambda (bytecode -> ast -> rapids)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2534dc6d4bb2c534cd0122e317317ad0459e4d3e): PUBDEV-1847: Cleaned up/refactored GBM/DRF
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ee3d12d04e2f23569e456436f880fb2e28223e62): Updated MeanSquare to Quadratic for DL
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fe94591e6ef84e1f2b051d18beece2b10006de7a): PUBDEV-2133: Speed up Enum mapping between train/test from O(N^2) to O(N*log(N))
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/60e8de2600d28fd8d7475ea9ae8b114913510ef9): Added GLRM scoring history with step size and average change in objective function value 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f7369945a480a3c29677ce7baa97c56166c4e0f2): SVD now outputs the V matrix as a frame with a frame key, rather than a double array in the API 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fc4aaaceb9e5c5b42f0c269b316b4d3f6f827a18): Modified k-means++ initialization in GLRM to set X to inverse of cluster distance with sum normalized to one, for each observation in training data
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/aad59cf147c887c4f709406393e8a242a49bc531): Increased GBM worker thread priority to avoid deadlock with high parallel GBM job counts
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e2be556f7ca9a10fae88747259f83f839e80d99b): Added input parameter `svd_method` to GLRM

#####Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2822e05775a157b6a64d31ab3cc5ae3bbccc4322): `centers_std` is now returned as a list of columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/48590f6e6affc3e12246cd78bdf82b9806d79f52): `str(Frame)` no longer returns an ID; updated ExprNode `_to_string` to accomodate
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e385e78271fb634c4e43e1d3c694ee6dfe955bff): Changed default setting for `_isAllAscii` to false
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/215348e1e743e1aa0fbddb7be937f58144d6b0e9): Fixed var to return scalar/frame based on `nrow`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/aadf7e1558bf673fc44bd345b9e1a592dc7242d6): Python now checks `ncol`, not `nrow`
- [PUBDEV-1060](https://0xdata.atlassian.net/browse/PUBDEV-1060): Python's `h2o.import_frame()` now matches R's `importFile()` parameters where applicable
- [PUBDEV-1960](https://0xdata.atlassian.net/browse/PUBDEV-1960): Python now uses the streaming endpoint `/3/DownloadDataset.bin`
- [PUBDEV-2223](https://0xdata.atlassian.net/browse/PUBDEV-2223): Added normalization and standardization coefficients to the model output in Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f556a8117cdcbd556421cb11076d8db9fa79a1f): Renamed `logging` to `h2o_logging` to avoid conflict with original logging package
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a902a8e16982d0e015436272272d6c2a2b551ea9): H2O now recognizes additional parameters (such as column names) for Python objects
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6877f2c69f42abc779df4bb98fc8b5d000a0bd88): `head` and `tail` no longer download the entire dataset
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1fd6ae0a988ca421dedf6338a895b53f9220d030): Truncated DF in `head` and `tail` before calling `/DownloadDataset`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/54279fbf168abc8dc45b080d47eebc6ea56e616d): `head()` and `tail()` now default to pretty printing in Python
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/56f1c364897c25b082f800fce9549160661fed03): Moved setup functionality from parse to parse setup; `col_types` and `na_strings` can now be dictionaries
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/adf612fd1f5c764a05231d6b8023c83ba9ffe0f5): Updated `H2OColSelect` to supply extra argument
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e5595ea79b9d4f3eb81dffc457c97180e6f078a): PUBDEV-2174: Relative tolerance is now used for floating point comparison
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d930dd90c2d77a903cb79e6f107f6cbe6823b94f): Added more cloud health output to `run.py`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/16dce03a13af46fae2fa912e79cd5fb073ca8477): When Pandas frames are returned, they are now wrapped to display nicely in iPython
 


#####R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/38fc561b542d0d17caf18eeee142034c935393a9): Added null check
- [PUBDEV-2185](https://0xdata.atlassian.net/browse/PUBDEV-2185): When appending a vec to an existing data frame, H2O now creates a new data frame while still keeping the original frame in memory
- [PUBDEV-1959](https://0xdata.atlassian.net/browse/PUBDEV-1959): R now uses the streaming endpoint `/3/DownloadDataset.bin`
- [PUBDEV-2020](https://0xdata.atlassian.net/browse/PUBDEV-2020): `h2o.splitFrame()` in R/Python now uses the `runif` technique instead of the horizontal slice technique
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fc8c337bad6178783286a262d0a18a246811e6fc): Changed `T`/`F` to `TRUE`/`FALSE`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0a2f64f526b35456614188806e38ed2c54ed8b5c): `xml2` package is now required for `rversions` package
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d3956c45a6de6e845ed9791f295195778902116e): Package dependencies are taken into account when installing R packages
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4c18d351207f2441e80a74e55df205edcaacbfcd): Metrics are now always computer if a dataset is provided (R `h2o.performance` call)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ff1f925b27a951608d5bbd66ee9487772e529b38): Column names are now fetched from H2O
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42c2bb48b3534ebb43a992b37ed3c683050e4aab): PUBDEV-2150: Time columns in H2O are now imported as Date columns in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9420547451c5ef6dcb04b8803d0a400c720445a4): `h2o.ls()` now returns `data.frame`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/69bd25b93763f7326d93447986a597cd283b4217): `h2o.ls()` now returns the whole frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a9326cd564b522dcb20aed91663f4390c8c218ef): Removed unnamed additional parameters (ellipses) in R algos
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c271c2e193c9e7581ee999db48fa9798997a66ee): Added `as.character`to Rapids implementation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e6df880496a67449693a25ab739682319ca2e6ab): Updated `plot.H2OModel` in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/808a64152e04ad9cc5a351001844a0a1fdfc907f): Updated scoring history plot in R for `training_frame` only
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/58c30eb4c94c06a78cd6a04a52cb84ebd97c1533): Instead of `:` and `assign`, `attr` is now used 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f3275a89227884e23cfabc535073dc08b8e7634d): Raw strings are now used as accessors
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1b319d837e0f18474419efe981e559a51606febb): `name.Frame` and `dimnames.Frame` are now visible


#####System

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cb9fe3fd132ce7851339b99420d1c25a0129160c): Added vertical prefetch of all chunks' worth of data for dense rows
- [PUBDEV-1426](https://0xdata.atlassian.net/browse/PUBDEV-1426): Scoring is now a non-blocking job with a progress bar
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c84c663c33963195f960f12819afa7624370764e): EasyPojo API is now serializable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/70d91bed44172257d6d804572c985ec1ec67201e): Changed parse setup guess when encountering large NA counts to not favor numeric over dates or UUIDs
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e0ed51ce8a7cc52ef522a15915f7444635ee2b5d): Refactored vector type conversion methods into a class called VecUtils
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/836c32ebd5a00b0c255ac3bdb418af2b5d4da81a): Cleaned up ASTStrList to handle frames with more than one vector during column conversion; checks types before converting; added several new column type conversions
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/678fa6af2b20105e17cacbe9c38bec4266b89246): If the job is cancelled, scoring is now canceled
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ad2548754ad909edb0f4b01c61c09708d8ba3ee6): Refactored `doAll_numericResult() -> doAll(nout, type, frame)` where all output vecs are of the given type
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5206760f20fa2394d16e30e4c025c6a3f4a62c44): Improved hash function
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/55874fa3e7c9ffbbdfb1cbb26d132304bf81deed): The output of `_train.get()` is now passed to a Frame
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/805f7a6cb58ac81720f65c0e895fab4433bb2972): Refactored binary/col ops for aesthetics and maintainability
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f1912e2e590e0011ac19ccd4910ce0216f0f8a2): Added correct types for new Vecs; `CategoricalWrappedVec` now exports a utility for enum conversions instead of a constructor
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f4fdf57c5a15ee36fc8ce8a0c3102a6a9500bd9f): Mean/sigma values are now printed to the logs after parsing
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4885b259b80e2001104de670c7fce1bcee149a17): PUBDEV-2174: Added some optimizations for some chunks (mostly integers) in RollupStats
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bcec3daaf504c392555a9d8b5f171cfa396be981): PUBDEV-2174: Added instantiations of Rollups for dense numeric chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5c30f2375b40f5443ed71ed75e56e654c8cdddf4): PUBDEV-2174: Implemented single-pass variance/stddev calculation for rollups
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/70d547db554b48a9f1a4915b556746fbe2ae0854): PUBDEV-2174: Added `hasNA()` for chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/655d36ddfb194606d251149021bddbd93ffa35d6): Reordered args in sub/gsub (`astid` > `astparameter`, `add string` -> `numeric`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f73199ab30c04c40aafa855b90cc1de2cce892d4): Ensured all chunks get closed
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9ae1d15a567cd92dfcef39adeddef8b10a7f3ced): `NewChunk.addString()` now accepts a Java string or BufferedString, eliminating needless conversion to a BufferedString before inserting into the NewChunk buffer. Improves efficiency of several ASTStrOps as well as converting Categorical columns to String columns.
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/68f7e5122364b938b560e68e0aac573c3ed198bd): Renamed enums to categoricals system-wide
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cd456388d1815cf08292459d44013d5e20436b49): Renamed `ValueString` -> `BufferedString`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5efb27dc1f50d5cad14fe86166060452bf16582f): Removed redundant frame creation; added Java comments to each string utility; changed RAPIDS name of `gsub` -> `replaceall` and `sub` -> `replacefirst`; added nchar utility to the R client; updated comments in Python and R client
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/06cf75e3786a14fb7cf846c9e34744b1cfead194): All NA chunks are now handled in string ops
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/2e87a2634fc48daf6996f9befb3c3d95e9d467cf): Added ability for string utils to handle NA chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7eba6217d3fe6f04a605b273a866d4e084a46208): Added the ability to handle duplicate rows to merge
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9e0c9fb38d885c54d811091448d134d09e08aaf3): `countMatches` utilities now only work on string columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e5cc404eec3e4a9e6ee9edf9e985cf487d4f91bd): Changed names of `SubStr` and `GSubStr` to `ReplaceFirst` and `ReplaceAll`; both methods now only accept string columns as input
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b71043473c652d0f457bdd00f4d1192090cbf210): Changed `toUpper` and `toLower` to only work on string columns; includes an optimzied version of each method as well as a UTF-safe version
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ab44f73178cfb711da8f5b65ee1b5c67e27213d4): CStrChunks now track whether they are pure ASCII to allow StringUtilities to use optimized versions of the utilities that operate directly on the string buffer
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bd7ba53f1a360a3b35b5b1e7b93306927a907ff1): Moved frame function to ArrayUtils
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1afa2701f352f03140a64d4a001e21fef2ead7a8): Removed categorical versions of `trim()` and `length()`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c92390122601d3abb2ff42b2fab85a9d49595025): Changed the merge defaults to match the implementation
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/5291941a8d132b60f3a22e04c0d21b6c4bd7d7d9): Merge no longer uses a `by` argument
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b318faf497c8989d900fa7f26a0c75a7ffe270cc): Added `trim` and `length` functionality for string columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/26421b79453623c961cc4f73ff48fd1cca95ceaa): HEXDEV-442: Improved POJO handling
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/146b656c3e94fd025cee0988abd8d0948e7ae94d): Config files are now transferred using a hexstring to avoid issues with Hadoop XML parsing
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6c8ddf330ae739117ab4302eb83f682f342e2a5e): HEXDEV-445: Added `isNA` check 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b340b63b221a765e9d2fb4b82283da92d997e3d7): Means, mults, modes, and size now do bulk rollups
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/823209767da4c162ad99047195d702769e7d37a8): Increased priority of model builder Driver classes to prevent deadlock when bulk-launching parallel unrelated model builds
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/207dee8a52d3fa7936eba9440ec8aed182c34e55): Renamed Currents to Rapids
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5e21d2f40a544303ef4acbe3f64f20d47d9d864): CRAN-based R clients are now set to opt-out by default
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/76d7a3307f7636660ca226ec65ce90fd7153257d): Assembly states are now saved in the DKV

#####Web UI

- [PUBDEV-1961](https://0xdata.atlassian.net/browse/PUBDEV-1961): Flow now uses the streaming endpoit `/3/DownloadDataset.bin`


####Bug Fixes

The following changes are to resolve incorrect software behavior: 

#####Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/798c098f42ad412e2331936238642dc7578450c8): Fixed bug with `CategoricalWrappedVec`
- [PUBDEV-1664](https://0xdata.atlassian.net/browse/PUBDEV-1664): Corrected math for GBM Tweedie with offsets/weights
- [PUBDEV-1665](https://0xdata.atlassian.net/browse/PUBDEV-1665): Corrected math for GBM Poisson with offsets/weights
- [PUBDEV-2130](https://0xdata.atlassian.net/browse/PUBDEV-2130): Deleting Deep Learning n-fold models resulted in a `java.lang.AssertionError`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e14e50d85922913ff5c6f0cb5a7c0806787d7be8): Fixed GLM with nfolds
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/463ac6aee7d656a61b45b29d517054e39300c126): Updated GLM InitTsk to run at +1 priority level to avoid deadlock when launching hundreds of GLMs in parallel
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/129c9fd062d1ba2b3a20c9d6ebe54d1a6d94b730): Column names (feature names) are now named correctly for the exported weight matrix connecting the input to the first hidden layer
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fa14eb4b07c320fb627476928a795693b0f4f6b9): Changed `isEnum` to `isCategorical`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/d8c287caa43aa005648c4f9b3c88aca7a09710d7): Cleaned up DRF and GBM; fixed checkpoint restart logic for trees and changed which parameters are configurable
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b3cbbf316e9b34e4da838fae902d1754d0bcd96b): Fixed incorrect logistic and hinge loss functions and apply to binary numeric columns in {0,1} only 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/dd67ef00b135c6d50f5fba28c3fde477f031a1eb): Fixed a bug where Poisson loss function was calculated incorrectly for values of 0
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c244867c3e4eb7813070c804882a88ab47c90f43): Fixed DL POJO for large input columns

#####Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/99f59e1161da82496aa3008592e1ff5d8826097e): `nrow` was not filling cache correctly
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e65bb079988ad7a19ee2fc0c17f93071f2ec4795): Fixed typo in Python object upload (`header` -> `col_header`)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f62055393b3273a67e9a5c02281d30e0dfca3392): Append now does so in place
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6ceab711563ec6b307656b0e2fad1a8bb7696fcf): Seed was not being set
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e0073fc3c03e779974896d259ff655e0b5cab8c9): Fixed `group_by`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/09779bb751c032c007f31b2408bfe7196a30c46f): Corrected `.fromPython`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4b078544fa49dc44ff59a920108f8d882d52bb3a): Corrected Python dict col names
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/85cf09508d1737ab9b3ad8381216e339b769b283): Fixed null/npe in H2O's fit for sklearn (Windows only)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ceb69702040279ff95e00dd4d5d179a30ddfcd67): `get_params` now keeps "algo" out of params
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/74969ec78970de85c8780380d6ae90d911bedcaa): Improved compatibility with sklearn by using "train" as a model build verb and reserving "fit" for sklearn; if "fit" method is attempted, a warning displays
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/05d2179e751b530561f2c36c3c65c98a28d73d21): Fixed accessor in Python model predict

#####R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7f5424abef8d8acb4c7160134643ca4122e7ff00): Fixed `is.numeric`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/56eef73e2f019b9d6cd49239d377d4899d1eb02c): Fixed `h2o.anyFactor` and `h2o.impute`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/05e130297684b584a62d2a8a0b16fab04a1af4f0): Fixed levels 
- [PUBDEV-1808](https://0xdata.atlassian.net/browse/PUBDEV-1808): `h2o.splitFrame` was not splitting randomly in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/903779fd8526f730091f70f52c67750554ed88fc): Fixed range in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f3a2a3f171910ef9121a8cfae86fc6932eb6a978): PUBDEV-2020: Fixed variable name for case where `destination_frame` is provided. 
- [PUBDEV-2198](https://0xdata.atlassian.net/browse/PUBDEV-2198): `h2o.table` ran slower than `h2o.groupby` by magnitudes
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/9cf2639f71e26d48aa2fdc1725ee61889a619239): Fixed location of datafile for for R example code
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bfe6ae201eee39b0ad32867a6322eaf6e23d4cb6): Fixed `length(column.names)==number_columns` check
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/573b25e3aaa2e7ea79d0713a93c4687d76fb0991): Parse types  can be specified by column index or column name, but not both
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0cb8b8dd69de20619318f1dd1bf9ff0c7a7f5481): Added connection (close HTTP header) to improve jetty connection pool behavior
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/3b9cc9b42c781d07e0cf9b455008690502b9a495): Added a sensible min on N
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/60ff4973c68eff876b63c286e04b0b2f3c166825): Added Windows binaries to R package repo
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ac3a755e8c4161bb6e696815a5f04774f3969e1e): Fixed `h2o.weights` to show frame as output
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/20827d5161b675628c543200304530c26860bfea): Fixed type conversion for time columns when ingested by `as.data.frame()`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/4c1e9e0098ca94b09e1f2fcb1cc96610c27daa30): Fixed `h2o.merge` R interface
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/cb8e2b5667021e452bdb69fe630c53270ad2062b): `head` and `tail` now always return `data.frame` 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e703181d0b545dc0be152bcac1d9e32f68f03f1a): Fixed a bug in GLRM init in R
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/613bd6ab537f09e24cb287addd0989178ee14134): Fixed bug in `h2o.summary` (constant categorical columns)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42f4635f6c5b17937b1eb58a46c505507979b89c): Fixed bug in `plot.H2OModel`

#####System

- [PUBDEV-2250](https://0xdata.atlassian.net/browse/PUBDEV-2250): During parsing, SVMLight-formatted files failed with an NPE [GitHub commit](https://github.com/h2oai/h2o-3/commit/d7c8d431a1bc08a64dc6e6233717dc7423ade58d)
- [PUBDEV-2213](https://0xdata.atlassian.net/browse/PUBDEV-2213): During parsing, alphanumeric data in a column was converted to missing values and the column was assigned a type of `int` 
- [PUBDEV-1990](https://0xdata.atlassian.net/browse/PUBDEV-1990): Spaces are now permitted in the Flow directory name
- [PUBDEV-1037](https://0xdata.atlassian.net/browse/PUBDEV-1037): Space in the user name was preventing H2O from starting
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/44994ee4998543ccf13c38e44017adee307db4da): Fixed `VecUtils.copyOver()` to accept a column type for the resulting copy
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a1f06c4ed21cbec4ac1c5f250f7cf5470758484a): Fixed `Vec.preWriting` so that it does not use an anonymous inner task which causes the entire Vec header to be passed
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bcec96c0fc088c5be2a923654a9581055f2ad969): Fixed parse to mark categorical references in ParseWriter as transient (enums must be node-shared during the entire multiple parse task)
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c5d5f166fce56fddd808fa6b1267b9c13d83063f): PUBDEV-2182: Fixed DL checkpoint restart with given validation set after R (currents) behavior changed; now the validation set key no longer necessarily matches the file name
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0f690db79d2df914d6ad2de2ca2feac6dc2ba48c): Fixed makeCon memory leak when `redistribute=T`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5dcd34febf1c289e1a86276b06122e6ddcbd3ec): PUBDEV-2174: Fixed sigma calculation for sparse chunks
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6c9fc7dac6c150b27989c5f4044ebd1df7c6e83e): Restored pre-existing string manipulation utilities for categorical columns
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a8b7cf2d80458116c8c2d5e8491285f197706859): Fixed syncRPackages task so it doesn't run during the normal build process
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ea3a86251e3ad99a1d1a04b60ead0f21925f7674): Fixed intermittent failures caused by different default timezone settings on different machines; sets needed timezone before starting test
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/fa3cad682e33aed6b4b8c7d7982bd13f600eb08f): Fixed error message for `countmatches`
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/02b09902d096a082296d76f097cbccfe3ac72dd5): PUBDEV-1443: Fixed size computation in merge
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b89399d88d1b10a67b9411897ed6dfbc68cb76bf): Fixed `h2o.tabulate()` to work in multi-node mode
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/8b1c75a85eadbc32aa9c2b4d4545252e468f79fc): Fixed integer overflow in printout of CM to TwoDimTable

---

###Slater (3.2.0.7) - 10/09/15

####Bug Fixes

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/bc6f15ab71f5d41553bbe566bcc0585ef2a2bdf1): Fix Java 6 compatibility

	The Java 7 API call
`_rawChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);`
has been replaced by the Java 6 API call
`_rawChannel.socket().setTcpNoDelay(true);`

  The Java 7 API call 
`sock.getRemoteAddress())`
has been replaced by
`sock.socket().getRemoteSocketAddress()`

---

###Slater (3.2.0.5) - 09/24/15

####Enhancements

#####Algorithms

* [PUBDEV-2133](https://0xdata.atlassian.net/browse/PUBDEV-2133): Enum test/train mapping is faster [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/fe94591e6ef84e1f2b051d18beece2b10006de7a)

- [PUBDEV-2030](https://0xdata.atlassian.net/browse/PUBDEV-2030): Improved POJO support to DRF

---

###Slater (3.2.0.3) - 09/21/15

####New Features

#####R

- [PUBDEV-2078](https://0xdata.atlassian.net/browse/PUBDEV-2078): H2O now returns per-feature reconstruction error for `h2o.anomaly()` [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/e818860af87cad796699e27f8dfb4ff6fc9354e8)

####Enhancements


#####Algorithms

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/1def2121ac811eebd9ea0e4ed9fa9f4d296a10ad): Added back support for sparse activations in DL; currently changes results as numerical values are de-scaled only, no standardized 

#####Python

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/f37ef949bd428d362b20f424f9df02761c33a419): Adjusted `import_file` in Python to accept the same parameters as `import_file` in R 


#####R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/b5e21d2f40a544303ef4acbe3f64f20d47d9d864): H2O now sets CRAN-based R clients to permanent opt-out. 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/0f2c3d67e4ab40d3fc2a5874acc1efce0bbe6bc4): Modified output of h2o.tabulate in R 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/e6cfe9d6408539645996486f10b579f9240e5b90): Added default plotting for models in R 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/a9ac5058c4f07e106d6b6b16002839cab8f9b2ef): Pre-pended graphics pkg to `plot.H2OModel` methods 


####Bug Fixes 

#####Algorithms

- [PUBDEV-2091](https://0xdata.atlassian.net/browse/PUBDEV-2091): All algos: when offset is the same as the response, all train errors should be zero [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/7515360a4c4181f639a18f70436f59969d4a0a46) 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/c244867c3e4eb7813070c804882a88ab47c90f43): Fixed DL POJO for large input columns

#####R

- [GitHub commit](https://github.com/h2oai/h2o-3/commit/43d22d18d284bf26b8553a31c02daf1ea3bb92d6): Fixed bugs in model 
plotting in R 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/ed4afe55e64ae681cf37e71179cbfa4a9c0f88c9): Fixed bugs in R plot.H2OModel for DL 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/42f4635f6c5b17937b1eb58a46c505507979b89c): Fixed bug in plot.H2OModel 

#####System


- [PUBDEV-1850](https://0xdata.atlassian.net/browse/PUBDEV-1850): Parse not setting NA strings properly [(GitHub commit)](https://github.com/h2oai/h2o-3/commit/6196b23ef68c364559fe304dbe342780fe8afbeb) 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/6d269ee2b59c71df178cae120c232f1551854700): H2O now escapes XML entities 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/afe4ff2f0dea41595a44eefa40fa256b353547f8): Fixed Java 6 build -replaced AutoCloseable with Closeable 
- [GitHub commit](https://github.com/h2oai/h2o-3/commit/7548c71de44e1e2adf4e165e0ec41105d0ac607b): Restored code that was needed for detecting NA strings 

---

###Slater (3.2.0.1) - 09/12/15


####New Features

#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/b33156815dc96167e2bf6f466e694e40ad813fcf): PUBDEV-1888: Added loss function calculation for DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/da8f65d3d8364b883937640e49a25785b9498d39): Set more parameters for GLM to be gridable.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5cfaacaf8f2cfc82024e371554f94326d4f4bce4): [KMeans] Enable grid search with max_iterations parameter.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c02b124b1daf3a0db504201fdc555e5f28a5a3e3): Add kfold column builders
- [GitHub](https://github.com/h2oai/h2o-3/commit/4ae83b726bc9e9128b8b0df81842d1c3c5df7b3c): Add stratified kfold method



#####Python

- [PUBDEV-684](https://0xdata.atlassian.net/browse/PUBDEV-684): Add nfolds to R/Python
- [GitHub](https://github.com/h2oai/h2o-3/commit/5b273a297d7b5b4b7e6131ed6c31cbb3d3d22638): Improved group-by functionality
- [GitHub](https://github.com/h2oai/h2o-3/commit/236c5af71093549108fa942847820a721da4880a): Added python example for downloading glm pojo.
- [GitHub](https://github.com/h2oai/h2o-3/commit/74c00f24777bd07bde05c2751204cccd7892ebcb): Added countmatches to Python along with a test.
- [GitHub](https://github.com/h2oai/h2o-3/commit/e94892ffab027282e4d96ffea89972f041367a77): Added support for getting false positive rates and true positive rates for all thresholds from binomial models; makes it easier to calculate custom metrics from ROC data (like weighted ROC)



#####R

- [PUBDEV-1788](https://0xdata.atlassian.net/browse/PUBDEV-1788): Added a factor function that will allow the user to set the levels for a enum column [GitHub](https://github.com/h2oai/h2o-3/commit/7999075a7cdcdc880d76a3be7e39edeb63d32fc8)
- [PUBDEV-1881](https://0xdata.atlassian.net/browse/PUBDEV-1881): Fixed bug in h2o.group_by for enumerator columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/af75976a238d22dd37048eb8d7c100c994bdac08): Refactor SVD method name and add `svd_method` option to R package to set preferred calculation method
- [PUBDEV-2071](https://0xdata.atlassian.net/browse/PUBDEV-2071): Accept columns of type integer64 from R through as.h2o()

#####Sparkling Water

- [PUBDEV-282](https://0xdata.atlassian.net/browse/PUBDEV-282): Support Windows OS in Sparkling Water

#####System

- [HEXDEV-120](https://0xdata.atlassian.net/browse/HEXDEV-120): Switch from NanoHTTPD to Jetty
- [GitHub](https://github.com/h2oai/h2o-3/commit/5987666d56d2a272b7b521cd9eb9cde3de6de0b0): Allow for "most" and "mode" in groupby
- [GitHub](https://github.com/h2oai/h2o-3/commit/930be126da18e6d4ed9078493dc788e22ea7e4c5): Added NA check to checking for matches in categorical columns
- [PUBDEV-1470](https://0xdata.atlassian.net/browse/PUBDEV-1470): Dropped UDP mode in favor of TCP
- [PUBDEV-1431](https://0xdata.atlassian.net/browse/PUBDEV-1431): /3/DownloadDataset.bin is now a registered handler in JettyHTTPD.java. Allows streaming of large downloads from H2O.[GitHub](https://github.com/h2oai/h2o-3/commit/a65a116875ca17eaf5b3535135f152781b51a40f)
- [PUBDEV-1865](https://0xdata.atlassian.net/browse/PUBDEV-1865): Implemented per-row 1D, 2D and 3D DCT transformations for signal/image/volume processing
- [PUBDEV-1686](https://0xdata.atlassian.net/browse/PUBDEV-1686): LDAP Integration
- [HEXDEV-381](https://0xdata.atlassian.net/browse/HEXDEV-381): LDAP Integration
- [HEXDEV-224](https://0xdata.atlassian.net/browse/HEXDEV-224): Added https support
- [GitHub](https://github.com/h2oai/h2o-3/commit/ced34107f71b5fe3c5ff830c827563be3d0c0286): Added mapr5.0 version to builds
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b92f571cd6c4710b5454a425fce090b99128b35): Add Vec.Reader which replaces lost caching

#####Web UI

- [GitHub](https://github.com/h2oai/h2o-3/commit/15eece855e8cfd1598aafadc42ffab9fb170e916): Disallow N-fold CV for GLM when lambda-search is on.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d3b7f01a10ff68c644f0a823051e23ebc4fc39f0): Added typeahead for http and https.
- [PUBDEV-1821](https://0xdata.atlassian.net/browse/PUBDEV-1821): Added Save Model and Load Model


####Enhancements

#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/7d96961462ea1d65e85c719b0310ea453206acb4): Don't allocate input dropout helper if `input_dropout_ratio = 0`.
- [PUBDEV-1920](https://0xdata.atlassian.net/browse/PUBDEV-1920): Datasets : Unbalanced sparse for binomial and multinomial
- [GitHub](https://github.com/h2oai/h2o-3/commit/463207ec042af9d05c9885daba78701440ceacb1): Major code cleanup for DL: Remove dead code, deprecate `sparse`/`col_major`.
- [PUBDEV-1942](https://0xdata.atlassian.net/browse/PUBDEV-1942): Use prior class probabilities to break ties when making labels [GitHub](https://github.com/h2oai/h2o-3/commit/f8b188e4775d0f3671c34b3c42fe9c417d960cfd)
- [GitHub](https://github.com/h2oai/h2o-3/commit/a57d0ff742c8d84189a42e340433bc79cc33e7d4): Update DL perf Rmd file to get the overall CM error.
- [GitHub](https://github.com/h2oai/h2o-3/commit/245a1dd8fc467eecd4102f906170cbc9eba38de0): Enable training data shuffling if `train_samples_per_iteration==0` and `reproducible==true`
- [GitHub](https://github.com/h2oai/h2o-3/commit/2369b555a68d02a8cff6d052870c8cb47bb52ec2): Checkpointing for DL now follows the same convention as for DRF/GBM.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6cb0fb3f2e7742597dd8fb2abce2cb22929f6782): No longer do sampling with replacement during training with `shuffle_training_data`
- [GitHub](https://github.com/h2oai/h2o-3/commit/d91daa179088dfd40df3bcd978c8f31a90419eaf): Add printout of sparsity ratio for double chunks.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5170b79e98f88bc7da40be53ab54ea41d4f624da): Check memory footprint for Gram matrix in PCA and SVD initialization
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4c4a4e8246749cc567b45603330f47917b009a2): Print more fill ratio debugging.
- [GitHub](https://github.com/h2oai/h2o-3/commit/a5f60727c887798ea4a305f28a3b529254f9764a): Fix the RNG for createFrame to be more random (since we are setting the seed for each row).
- [PUBDEV-2010](https://0xdata.atlassian.net/browse/PUBDEV-2010): Improve reporting of unstable DL models [GitHub](https://github.com/h2oai/h2o-3/commit/d6c1c4a833d82f89281024a3337fb847b0df407c)
- [PUBDEV-2018](https://0xdata.atlassian.net/browse/PUBDEV-2018): Improve auto-tuning for DL on large clusters / large datasets [GitHub](https://github.com/h2oai/h2o-3/commit/861763c1527372e9f65ee15a21af801db8ce3844)
- [GitHub](https://github.com/h2oai/h2o-3/commit/c9dcc80dd888e1d1c33e231aee52aec177ec93ac): Add input parameter to h2o.glrm indicating whether to ignore constant columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/2dae22965b9f9a876bcb6198e3bf6340ad1f781b): Missing enums are imputed using the majority class of the column. For other types of missing categorical, just round the mean to the nearest integer.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c0e21a977fcc436e984da42158f7b3184e3b63f2): Skip rows in training frame with missing value(s) if requested
- [GitHub](https://github.com/h2oai/h2o-3/commit/f96b53d2d688201133c15d87b4a4ef06c71bfbfb): Speed up direct SVD by working with transpose directly
- [GitHub](https://github.com/h2oai/h2o-3/commit/5703a88bf630c1ab61a54ec7523ee2587d8309a2): Fix a bug in initialization of SVD and change l2 norm to sum of squared error in convergence test.
- [GitHub](https://github.com/h2oai/h2o-3/commit/88ed523b674588607a8af7c27357bef4aa042b49): Use absolute value for mean weight and bias checks.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6b1073b310d3058b44ac2e10546115338fa5ac23): No longer leak constant chunks during AE scoring/reconstruction.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c5f4c10a7df0cf81f659f09b0814bd76f1c181dc): No longer differentiate between DL model instabilitites (weights vs biases). 
- [GitHub](https://github.com/h2oai/h2o-3/commit/791f9878007a6a5bd3ecf2794f7b538c8f9cd66a): Make method static, where possible.
- [GitHub](https://github.com/h2oai/h2o-3/commit/14dedf6dee5a33622f4d9a2be6d5b64627b8273c): Make GLRM seeding independent of number of chunks.


#####API

- [GitHub](https://github.com/h2oai/h2o-3/commit/9e2e14c1ce782e0016cb632e9130ce291826b97e): Added REST end-points for glrm,svd,pca,naive bayes algorithms.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bd38de613dad5528979d801007f080afc221b15e): Added unicode to frame getter possibilities
- [GitHub](https://github.com/h2oai/h2o-3/commit/0dab2b0232e0b21cd658ec765e57c8c93836d1ec): Added proper lookup of offset/weights/fold_column
- [GitHub](https://github.com/h2oai/h2o-3/commit/65a43018a6de8df24eef27065916ac33c3c0074f): Data should be eagered before download_csv.
- [GitHub](https://github.com/h2oai/h2o-3/commit/99b0fa76e6efe9e268989985967fa545195e2b53): Simplified model builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/508ad0e28f40f537e906a372a2760ca6730ebe94): Added None as default for "on" field
- [GitHub](https://github.com/h2oai/h2o-3/commit/f227b8e730314ab3bd30269d44d24ef6c79383cb): Removed all of the unnecessary calls to h2o.init and removed the unnecessary environment variable for version checking during testing
- [PUBDEV-2064](https://0xdata.atlassian.net/browse/PUBDEV-2064): rename the coordinate decent solvers in the REST API / Flow to <mumble> (experimental)


#####Grid Search

- [GitHub](https://github.com/h2oai/h2o-3/commit/83991bb91c7a523e32f8e106d91b7bb7343655f8): Added check that x is not null before verifying data in unsupervised grid search algorithm
- [GitHub](https://github.com/h2oai/h2o-3/commit/88553f6423e1a9713aa4325f2ca540491f5ea27b): Made naivebayes parameters gridable.
- [PUBDEV-1933](https://0xdata.atlassian.net/browse/PUBDEV-1933): Called drf as randomForest in algorithm option [GitHub](https://github.com/h2oai/h2o-3/commit/0334fa3cd76653dfe6233013247ee7fe7d68abfd)
- [GitHub](https://github.com/h2oai/h2o-3/commit/ef75ceaaab345959f9af07921cc2fcc4272f181a): Validation of grid parameters against algo `/parameters` rest endpoint.
- [PUBDEV-1979](https://0xdata.atlassian.net/browse/PUBDEV-1979): Train N-fold CV models in parallel [GitHub](https://github.com/h2oai/h2o-3/commit/108e097babe1c97cb83912f04fd68f444b5c6fc1)
- [PUBDEV-1978](https://0xdata.atlassian.net/browse/PUBDEV-1978): grid: would be good to add to h2o.grid R help example, how to access the individual grid models 


#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/e448879646b01052b565662d605dead4c690290d): Refactored into h2o.system_file so it's parallel to R client.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d7e7172e0ea4282e4c842e083fb797b2567d5e3b): Added `h2o_deprecated` decorator
- [GitHub](https://github.com/h2oai/h2o-3/commit/8dd038f89ad3e5996b6ef0d2eaec68e00f881583): Use `import_file` in `import_frame`
- [GitHub](https://github.com/h2oai/h2o-3/commit/625b22d4f1731f7cc69d2b7fabb75868722dce78): Handle a list of columns in python group-by api
- [GitHub](https://github.com/h2oai/h2o-3/commit/e0b700f524ec589502708d62f5f16189db317a47): Use pandas if available for twodimtables and h2oframes
- [GitHub](https://github.com/h2oai/h2o-3/commit/684bfde2da68b6e2d23929909a3fe099d1f23e9c): Transform the parameters list into a dict with keys being the parameter label
- [GitHub](https://github.com/h2oai/h2o-3/commit/22fd873952c4ae30853a7bc459c170ae7f3a1aa4): Added pop option which does inplace update on a frame (Frame.remove)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9c57681c67d2fbd9a52a7779bcc4f336c7c45d42): ncol,dim,shape, and friends are now all properties
- [PUBDEV-193](https://0xdata.atlassian.net/browse/PUBDEV-193): Write python version of h2o.init() which knows how to start h2o
- [PUBDEV-1903](https://0xdata.atlassian.net/browse/PUBDEV-1903): Method to get parameters of model in Python API
- [GitHub](https://github.com/h2oai/h2o-3/commit/4048429c7f9af76a993f16f883e0b81dc827427a): Allow for single alpha specified not be in a list
- [GitHub](https://github.com/h2oai/h2o-3/commit/f19f4cf730f57d872f8685ee3eecc604be1f74a2): Updated endpoint for python client `download_csv`
- [GitHub](https://github.com/h2oai/h2o-3/commit/311762edf2cb2396bc708b6dc4fe5236a7a92566): Allow for enum in scale/mean/sd (ignore or give NA)
- [GitHub](https://github.com/h2oai/h2o-3/commit/c2b15c340196f08f7b8dfbdfe4936cb1d24e0ee4): Allow for `n_jobs=-1` and `n_jobs > 1` for Parallel jobs
- [GitHub](https://github.com/h2oai/h2o-3/commit/650525f327d142ad8a3048ef742874637ab92d58): Added `frame_id` property to frame
- [GitHub](https://github.com/h2oai/h2o-3/commit/268e3791c211a00d03af7d01998443b1fb8b6080): Removed remaining splats on dicts
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ead75d3e83a34d479617a7dbd18748ada599d0d): Removed need to splat pass thru args
- [GitHub](https://github.com/h2oai/h2o-3/commit/802aa472f41030884e7f15b1ad13e5a9e555851c): Added `get_jar` flag to `download_pojo`

#####R

- [PUBDEV-1866](https://0xdata.atlassian.net/browse/PUBDEV-1866): Rewrote h2o.ensemble to utilize nfolds/fold_column in h2o base learners
- [GitHub](https://github.com/h2oai/h2o-3/commit/44581bb46e43ef20100249cf7271590aaf3953a2): Added `max_active_predictors`.
- [GitHub](https://github.com/h2oai/h2o-3/commit/58143a4b6fc49d1974b64dc16afdabc8e5b1d621): Updated REST call from R for model export
- [PUBDEV-1853](https://0xdata.atlassian.net/browse/PUBDEV-1853): Removed addToNavbar from RequestServer [GitHub](https://github.com/h2oai/h2o-3/commit/9362909fbce887c282783da6e9efa9e3a9a9b96c)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b4f8818eba32abfe4b393d2340df793204abe0d): Add "Open H2O Flow" message.
- [GitHub](https://github.com/h2oai/h2o-3/commit/3b3bc6fc67237306495433b030833a7f6d3e603f): Replaced additive float op by multiplication
- [GitHub](https://github.com/h2oai/h2o-3/commit/70a2e5d859f6c6f9075bdfb93e77aa12c23b1074): Reimplement checksum for Model.Parameters
- [GitHub](https://github.com/h2oai/h2o-3/commit/df6cc628edf2577d05fd0deae68a0d63b04d11c4): Remove debug prints.
- [PUBDEV-1857](https://0xdata.atlassian.net/browse/PUBDEV-1857): Removed the need for String[] path_params in RequestServer.register() [GitHub](https://github.com/h2oai/h2o-3/commit/5dfca019b1c69c2814911bdfe485fc888525ec99)
- [PUBDEV-1856](https://0xdata.atlassian.net/browse/PUBDEV-1856): Removed the writeHTML_impl methods from all the schemas
- [PUBDEV-1854](https://0xdata.atlassian.net/browse/PUBDEV-1854): Made _doc_method optional in the in Route constructors [GitHub](https://github.com/h2oai/h2o-3/commit/a0bd6d7bf065bc78ac34864c1e095ed53dacd5a1)
- [PUBDEV-1858](https://0xdata.atlassian.net/browse/PUBDEV-1858): Changed RequestServer so that only one handler instance is created for each Route
- [GitHub](https://github.com/h2oai/h2o-3/commit/1b8e6f2b5f7ddb0e9ae7b976c14f03ae4de8c627): Swapped out rjson for jsonlite for better handling of odd characters from dataset.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b12175ee2cde22172b61ee49e492f9acff2421bd): Prettify R's grid output.
- [PUBDEV-1841](https://0xdata.atlassian.net/browse/PUBDEV-1841): R now respects the TwoDimTable's column types
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e9161f1682df37736451b74465f5bb422d64cc5): Fixes show method for grid object when `hyper_params` is empty.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8c77c2ef785292adec6616f5243a38a6e3105ebc): h2o.levels returns R vector for single column
- [GitHub](https://github.com/h2oai/h2o-3/commit/08f1e95f17c5dfbff8bcdea6ef3a460751fa8ba2): Uses PredictCsv from genmodel now.
- [GitHub](https://github.com/h2oai/h2o-3/commit/5cbf42e300df0ef45f0bd4558fa5a339ea97cdaf): Exposed stacktraces in R's summary() call.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4dac1e2fb230f7740540ffa9f9379e03a626935): print type of failed value in $<-
- [GitHub](https://github.com/h2oai/h2o-3/commit/050956e1562ee51ad7de4cd93dcc0e6195b83445): allow value to be integer in $<-
- [GitHub](https://github.com/h2oai/h2o-3/commit/785111016a2c7a7fcdaa90846d725caadb0f9192): Check for `is_client` being NULL since older H2O clusters may not have `is_client`. 


#####Sparkling Water

- [GitHub](https://github.com/h2oai/h2o-3/commit/5035be242cb6f3a594902cb730301fcd7d2cfec6): Copy content of h2o-dist into target directory.

#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/cf80c73439b56340688481a473a18c61ccbe0818): Rename label fields in prediction object.
- [GitHub](https://github.com/h2oai/h2o-3/commit/32685d0413bab4a5c142e2fb79205e6d5062ad69): Uses the original Vec's domain in alignment
- [GitHub](https://github.com/h2oai/h2o-3/commit/3cd21a2ee87ec088f5e2bbd99a56cdf0247dc790): Added columnName and unknownLevel to PredictUnknownCategoricalLevelException. 
- [PUBDEV-1559](https://0xdata.atlassian.net/browse/PUBDEV-1559): Added compression of 64-bit Reals  [GitHub](https://github.com/h2oai/h2o-3/commit/5ef3008351b36fd8b1261d162cce6e60a071a462)
- [GitHub](https://github.com/h2oai/h2o-3/commit/4971f63b283fcb90ffa076574d4ff597a8ae4356): Added time information to buildinfo.json.
- [GitHub](https://github.com/h2oai/h2o-3/commit/9c33e5718f0ce9cb7220ea6f5152b0e751e5ec50): Put build metadata into a json file.
- -[GitHub](https://github.com/h2oai/h2o-3/commit/4971f63b283fcb90ffa076574d4ff597a8ae4356): Add time information to buildinfo.json.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1d22bfa9fb23b0ebe1b9a389688c5701c0362033): Delete any prior main CV models of the same key if CV model building is cancelled before the main model started to build. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/524d94d31f40d19e1fc055995860a6d4bdba7b67): Change loading name parameter to a String to address a Flow issue. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/48e7aa47cacade1c964d89920e0218ebedae182b): Remove extra assertion to avoid NPEs after client call of bulk remove after done() is called but before the finally is done with updateModelOutput.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c5a52b88746e8c82c04ea01cbb2c4a6910749a34): Ensures that date time methods return year/month/day values in the currently set timezone.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8789d8a1bbf8260d596bcc1692fe1f41dcdf81aa): Frees memory from streamed zip reads after the chunk has been parsed. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/c6c76e04b7efb1a3be470603e7c4b44dc8cc7767): Unifies categorical strings to UTF-8 and warns the user about all conversion.
- [GitHub](https://github.com/h2oai/h2o-3/commit/917bfe406fb59d7a0d9deecea524a91dd049073a): add isNA checks to scale
- [GitHub](https://github.com/h2oai/h2o-3/commit/c98348d7d4f4883d887047670783dbb9ed3eeb32): Do not start UDPRecevier thread (unless running with useUDP option) 


#####Web UI

- [PUBDEV-1961](https://0xdata.atlassian.net/browse/PUBDEV-1961): Flow: use streamining endpoint /3/DownloadDataset.bin



####Bug Fixes 

#####Algorithms

- [PUBDEV-1785](https://0xdata.atlassian.net/browse/PUBDEV-1785): Deadlock while running GBM
- [GitHub](https://github.com/h2oai/h2o-3/commit/b2fd9150aeb8c0816d2e09a09bc133517d6aa72f): Fix name for `standardized_coefficient_magnitudes`.
- [PUBDEV-1774](https://0xdata.atlassian.net/browse/PUBDEV-1774): Setting gbm's balance_classes to True produces suspect models 
- [PUBDEV-1849](https://0xdata.atlassian.net/browse/PUBDEV-1849): K-Means: negative sum-of-squares after mean imputation
- [GitHub](https://github.com/h2oai/h2o-3/commit/09a73ba1d1f5b24b56af842d75259df4ae52af96): Set the iters counter during kmeans center initialization correctly
- [GitHub](https://github.com/h2oai/h2o-3/commit/bfa9cd5179f7b1dce85895db80b28ec9ec743f71): fixed parenthesis in GLM POJO generation
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed0dfe29aab903586a64565a531ecc52b3414dce): Should be updating model each iteration with the newly fitted kmeans clusters, not the old ones!
- [PUBDEV-1867](https://0xdata.atlassian.net/browse/PUBDEV-1867): GLRM with Simplex Fails with Infinite Objective
- [PUBDEV-1666](https://0xdata.atlassian.net/browse/PUBDEV-1666): GBM:Math correctness for Gamma with offsets/weights
- [PUBDEV-451](https://0xdata.atlassian.net/browse/PUBDEV-451): Trees in GBM change for identical models [GitHub](https://github.com/h2oai/h2o-3/commit/7838e6773fb128c4ab549930c167fd109369fc29)
- [PUBDEV-1924](https://0xdata.atlassian.net/browse/PUBDEV-1924): R^2 stopping criterion isn't working [GitHub](https://github.com/h2oai/h2o-3/commit/b074b2a1d0915f701a8c06e69fa53e7fd0cb8cdc)
- [PUBDEV-1776](https://0xdata.atlassian.net/browse/PUBDEV-1776): GLM: cross-validation bug  [GitHub](https://github.com/h2oai/h2o-3/commit/0cfdd972c118ff158c88ffa4b627de8a245ecaba)
- [PUBDEV-1682](https://0xdata.atlassian.net/browse/PUBDEV-1682): GLM : Lending club dataset => build GLM model => 100% complete => click on model => null pointer exception [GitHub](https://github.com/h2oai/h2o-3/commit/676dff79ec179059e9aa77a09357091369f47791)
- [PUBDEV-1987](https://0xdata.atlassian.net/browse/PUBDEV-1987): error returned on prediction for xval model
- [PUBDEV-1928](https://0xdata.atlassian.net/browse/PUBDEV-1928): Properly implement Maxout/MaxoutWithDropout [GitHub](https://github.com/h2oai/h2o-3/commit/633288f3ceaaa4e4f4a0f39d6d6d1f1b635711c5)
- [GitHub](https://github.com/h2oai/h2o-3/commit/669e364d1ca519bebc87781341f226ecadcefff0): print actual number of columns (was just #cols) in DRF init
- [PUBDEV-2026](https://0xdata.atlassian.net/browse/PUBDEV-2026): Fix setting the proper job state in DL models [GitHub](https://github.com/h2oai/h2o-3/commit/e564e70404afb06e29c4cdb806787a9998f98124)
- [PUBDEV-1950](https://0xdata.atlassian.net/browse/PUBDEV-1950): Splitframe with rapids is not blocking
- [PUBDEV-1995](https://0xdata.atlassian.net/browse/PUBDEV-1995): nfold: when user cancels an nfold job, fold data still remains in the cluster memory 
- [PUBDEV-1994](https://0xdata.atlassian.net/browse/PUBDEV-1994): nfold: cancel results in a  java.lang.AssertionError
- [PUBDEV-1910](https://0xdata.atlassian.net/browse/PUBDEV-1910): Canceled GBM with CV keeps lock
- [GitHub](https://github.com/h2oai/h2o-3/commit/02c3b4a20c09a52924a588118a4032edc2208538): Fix DL checkpoint restart with new data.


#####API 

- [PUBDEV-1955](https://0xdata.atlassian.net/browse/PUBDEV-1955): Change Schema behavior to accept a single number in place of array [GitHub](https://github.com/h2oai/h2o-3/commit/4451e192b58233180371047acb4336aae2629210)
- [PUBDEV-1914](https://0xdata.atlassian.net/browse/PUBDEV-1914): Iced deserialization fails for Enum Arrays



#####Grid

- [PUBDEV-1876](https://0xdata.atlassian.net/browse/PUBDEV-1876): Grid: progress bar not working for grid jobs
- [PUBDEV-1875](https://0xdata.atlassian.net/browse/PUBDEV-1875): Grid: the meta info should not be dumped on the R screen, once the grid job is over
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed586eec9ea847fb10888d6a9caa55766a680678): [PUBDEV-1876] Fix grid update.
- [PUBDEV-1874](https://0xdata.atlassian.net/browse/PUBDEV-1874): Grid search: observe issues with model naming/overwriting and error msg propagation [GitHub](https://github.com/h2oai/h2o-3/commit/92a5eaeac52628d8ced9e48487c8b4d6ed003e23)
- [HEXDEV-402](https://0xdata.atlassian.net/browse/HEXDEV-402): R: kmeans grid search doesn't work
- [PUBDEV-1901](https://0xdata.atlassian.net/browse/PUBDEV-1901): Grid appends new models even though models already exist.
- [PUBDEV-1874](https://0xdata.atlassian.net/browse/PUBDEV-1874): Grid search: observe issues with model naming/overwriting and error msg propagation
- [PUBDEV-1940](https://0xdata.atlassian.net/browse/PUBDEV-1940): Grid: glm grid on alpha fails with error "Expected '[' while reading a double[], but found 1.0"
- [PUBDEV-1877](https://0xdata.atlassian.net/browse/PUBDEV-1877): Grid: if user specify the parameter value he is running the grid on, would be good to warn him/her 
- [PUBDEV-1938](https://0xdata.atlassian.net/browse/PUBDEV-1938): Grid: randomForest: unsupported grid params and wrong error msg

#####Hadoop

- [PUBDEV-2036](https://0xdata.atlassian.net/browse/PUBDEV-2036): importModel from hdfs doesn't work
- [PUBDEV-2027](https://0xdata.atlassian.net/browse/PUBDEV-2027): Clicking shutdown in the Flow UI dropdown does not exit the Hadoop cluster


#####Python

- [PUBDEV-1789](https://0xdata.atlassian.net/browse/PUBDEV-1789): Python client h2o.remove_vecs (ExprNode) makes bad ast
- [PUBDEV-1795](https://0xdata.atlassian.net/browse/PUBDEV-1795): Unable to read H2OFrame from Python
- [PUBDEV-1764](https://0xdata.atlassian.net/browse/PUBDEV-1764): Python importFile does not import all files in directory, only one file [GitHub](https://github.com/h2oai/h2o-3/commit/7af19a70c5ff5887feab1732d444ce345d0737b7)
- [GitHub](https://github.com/h2oai/h2o-3/commit/0d8e8bcb74e89505324d8c2a3b795680a33aeea7): parameter name is "dir" not "path"
- [PUBDEV-1693](https://0xdata.atlassian.net/browse/PUBDEV-1693): Python: Options for handling NAs in group_by is broken
- [PUBDEV-1415](https://0xdata.atlassian.net/browse/PUBDEV-1415): Intermittent Unimplemented rapids exception: pyunit_var.py . Also prior test got unimplemented too, but test didn't fail (client wasn't notified)
- [PUBDEV-1119](https://0xdata.atlassian.net/browse/PUBDEV-1119): Python: Need to be able to access resource genmodel.jar
- [GitHub](https://github.com/h2oai/h2o-3/commit/fb651714adcff4a407442160173c6499faabc79b): Fix download of pojo in Python.


#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/6851fa43a971668157e9f16aeda1446043760e0b): Fixed bug in `h2o.ensemble .make_Z` function
- [PUBDEV-1796](https://0xdata.atlassian.net/browse/PUBDEV-1796): R: h2o.importFile doesn't allow user to choose column type during parse
- [PUBDEV-1768](https://0xdata.atlassian.net/browse/PUBDEV-1768): R: Fails to return summary on subsetted frame [GitHub](https://github.com/h2oai/h2o-3/commit/025500c8f48c9a61053238da7b3630e04386ac32)
- [PUBDEV-1909](https://0xdata.atlassian.net/browse/PUBDEV-1909): R: Adding column to frame changes string enums in column to numerics
- [PUBDEV-1936](https://0xdata.atlassian.net/browse/PUBDEV-1936): R: h2o.levels return only the first factor of factor levels
- [PUBDEV-1869](https://0xdata.atlassian.net/browse/PUBDEV-1869): R: sd function should convert enum column into numeric and calculate standard deviation [GitHub](https://github.com/h2oai/h2o-3/commit/6e9c562b72f79e2fd1d579fc9ca2a784acddab80)
- [PUBDEV-1246](https://0xdata.atlassian.net/browse/PUBDEV-1246): R: h2o.hist needs to run pretty function for pretty breakpoints to get same results as R's hist [GitHub](https://github.com/h2oai/h2o-3/commit/f3e935bba3e94e5ece27b67856da45e0ac616431)
- [PUBDEV-1868](https://0xdata.atlassian.net/browse/PUBDEV-1868): R: h2o.performance returns error (not warning) when model is reloaded into H2O
- [PUBDEV-1723](https://0xdata.atlassian.net/browse/PUBDEV-1723): h2o R : subsetting data :h2o removing wrong columns, when asked to delete more than 1 columns
- [GitHub](https://github.com/h2oai/h2o-3/commit/425dbd9ee95487bce5424ed76e919821da0a2b10): fix h2o.levels issue
- [PUBDEV-1972](https://0xdata.atlassian.net/browse/PUBDEV-1972): R: setting weights_column = NULL causes unwanted variables to be used as predictors




#####Sparkling Water

- [PUBDEV-1173](https://0xdata.atlassian.net/browse/PUBDEV-1173): create conversion tasks from primitive RDD
- [GitHub](https://github.com/h2oai/h2o-3/commit/f45577b04a16221b53e2c59d1388ab8e8e2de87a): Fix return value issue in distribution script.


#####System

- [HEXDEV-360](https://0xdata.atlassian.net/browse/HEXDEV-360): getFrame fails on Parsed Data
- [PUBDEV-366](https://0xdata.atlassian.net/browse/PUBDEV-366): Fix parsing for high-cardinality categorical features [GitHub](https://github.com/h2oai/h2o-3/commit/99ae85dbe9305992a8e3f4b9e792c8f73e61d2fa)
- [PUBDEV-1143](https://0xdata.atlassian.net/browse/PUBDEV-1143): Parse: Cancel parse unreliable; does not work at all times
- [PUBDEV-1872](https://0xdata.atlassian.net/browse/PUBDEV-1872): Ability to ignore files during parse [GitHub](https://github.com/h2oai/h2o-3/commit/43e08765d1d6be49a9a6a83daf57ef1aaa511061)
- [PUBDEV-777](https://0xdata.atlassian.net/browse/PUBDEV-777): Parse : Parsing compressed files takes too long
- [PUBDEV-1916](https://0xdata.atlassian.net/browse/PUBDEV-1916): Parse: 2 node cluster takes 49min vs  40sec on a 1 node cluster [GitHub](https://github.com/h2oai/h2o-3/commit/cb720caae81ac754a7cbb0452958d9cb7c92c2d8)
- [PUBDEV-1431](https://0xdata.atlassian.net/browse/PUBDEV-1431): Convert /3/DownloadDataset to streaming
- [PUBDEV-1995](https://0xdata.atlassian.net/browse/PUBDEV-1995): nfold: when user cancels an nfold job, fold data still remains in the cluster memory 
- [PUBDEV-1994](https://0xdata.atlassian.net/browse/PUBDEV-1994): nfold: cancel results in a  java.lang.AssertionError
- [PUBDEV-1910](https://0xdata.atlassian.net/browse/PUBDEV-1910): Canceled GBM with CV keeps lock [GitHub](https://github.com/h2oai/h2o-3/commit/848c015b1ac6dee7517bd9d82eeae2919d01c9c6)
- [PUBDEV-1992](https://0xdata.atlassian.net/browse/PUBDEV-1992): CreateFrame isn't totally random
- [GitHub](https://github.com/h2oai/h2o-3/commit/6b12de4c31e5401ae0b7bd3283092f68b99bb45a): Fixes a bug that allowed big buffers to be constantly reallocated when it wasn't needed. This saves memory and time.
- [GitHub](https://github.com/h2oai/h2o-3/commit/009e888d07e89dcbab51556cb56bcbc6eaffdedc): Fix print statement.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4c40cf1815c27369aa12c7c0ed19272cbd4a7499): Fixed orderly shutdown to work with flatfile.
- [PUBDEV-1998](https://0xdata.atlassian.net/browse/PUBDEV-1998): Parse  : Lending club dataset parse => cancelled by user
- [PUBDEV-2028](https://0xdata.atlassian.net/browse/PUBDEV-2028): Shutdown => unimplemented error on  curl -X POST 172.16.2.186:54321/3/Shutdown.html
- [PUBDEV-2070](https://0xdata.atlassian.net/browse/PUBDEV-2070): Download frame brings down cluster
- [PUBDEV-2067](https://0xdata.atlassian.net/browse/PUBDEV-2067): Cannot mix negative and positive array selection
- [PUBDEV-2024](https://0xdata.atlassian.net/browse/PUBDEV-2024): Save model to HDFS fails



#####Web UI

- [PUBDEV-2012](https://0xdata.atlassian.net/browse/PUBDEV-2012): Histograms in Flow are slightly off
- [PUBDEV-2029](https://0xdata.atlassian.net/browse/PUBDEV-2029): exportModel from Flow to HDFS doesn't work



---

###Simons (3.0.1.7) - 8/11/15


####New Features
The following changes represent features that have been added since the previous release:

#####Python

- [PUBDEV-684](https://0xdata.atlassian.net/browse/PUBDEV-684): Add nfolds to R/Python


#####Web UI

- [HEXDEV-390](https://0xdata.atlassian.net/browse/HEXDEV-390): Print Flow to PDF / Printer


####Enhancements
The following changes are improvements to existing features (which includes changed default values):



#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/a15490b3507265e2768698ca768c98b6eb40d85b): add seed to the model building that uses balance_classes, for determinism/repeatability 
- [GitHub](https://github.com/h2oai/h2o-3/commit/fa4118960f5ab97f391ff86b077482ee2575c6a4): Reduce the frequency at which tiny tree models are printed to stdout: Only print during the first 4 seconds if `score_each_iteration` is enabled.
- [GitHub](https://github.com/h2oai/h2o-3/commit/610e3fb8c3df087c9f21b19a33e97d53f0829e6e): Only call the limited printout for TwoDimTables during Model.toString () that prints all TwoDimTables of the model._output.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0898eff8d2c7a01f29a23754eab2b0d954480faf): Only print up to 10 rows of TwoDimTables in ASCII logs (first/last 5).
- [GitHub](https://github.com/h2oai/h2o-3/commit/77e62aa916c11f57aa66cec12ccb707d946f76a3): Remove some overflow/underflow checks: Let exp(x) be small and log(x) be large.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1e0945ea13bc18f2d5dcd8fee270e52be2bb250b): Add `nbins_top_level` parameter to DRF/GBM. Not yet in R.
- [GitHub](https://github.com/h2oai/h2o-3/commit/15eece855e8cfd1598aafadc42ffab9fb170e916): Disallow N-fold CV for GLM when lambda-search is on.


#####API

- [GitHub](https://github.com/h2oai/h2o-3/commit/a01fde0c855e9194a1fead2f39587d958f915438): Cleanup of public API of Schema.java. Improve its JavaDoc a lot.


#####Python

- [PUBDEV-1765](https://0xdata.atlassian.net/browse/PUBDEV-1765): Improve python online documentation
- [PUBDEV-1497](https://0xdata.atlassian.net/browse/PUBDEV-1497): Python : Weights R tests to be ported from R for GLM/GBM/RF/DL
- [GitHub](https://github.com/h2oai/h2o-3/commit/b53a2e564e0b85d6fb2da54a5ff232e20829a967): adjust to split frame jobs result
- [GitHub](https://github.com/h2oai/h2o-3/commit/05516cb5a2297a25125e80603c55e97fc2e5b92b): allow for update thingy to be a tuple (so rows and columns)
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ebfd7c7ec6ff56732af96ffdb0b90908c9c0f01): when starting h2o jvm with h2o.init(), give h2o child process different id than parent, so it doesn't get killed on Ctrl-C
- [GitHub](https://github.com/h2oai/h2o-3/commit/71b6ea7fa61b0fe9962b6a5536e13e661ed5e656): add option to turn off progress bar print out
- [GitHub](https://github.com/h2oai/h2o-3/commit/bd38de613dad5528979d801007f080afc221b15e): add unicode to frame getter possibilities
- [GitHub](https://github.com/h2oai/h2o-3/commit/268e3791c211a00d03af7d01998443b1fb8b6080): remove remaining splats on dicts
- [GitHub](https://github.com/h2oai/h2o-3/commit/8ead75d3e83a34d479617a7dbd18748ada599d0d): no need to splat pass thru args
- [GitHub](https://github.com/h2oai/h2o-3/commit/0dab2b0232e0b21cd658ec765e57c8c93836d1ec): proper lookup of offset/weights/fold_column
- [GitHub](https://github.com/h2oai/h2o-3/commit/65a43018a6de8df24eef27065916ac33c3c0074f): data should be eagered before download_csv.
- [GitHub](https://github.com/h2oai/h2o-3/commit/99b0fa76e6efe9e268989985967fa545195e2b53): simplify model builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/508ad0e28f40f537e906a372a2760ca6730ebe94): use None as default for "on" field
- [GitHub](https://github.com/h2oai/h2o-3/commit/802aa472f41030884e7f15b1ad13e5a9e555851c): add `get_jar` flag to `download_pojo`
- [GitHub](https://github.com/h2oai/h2o-3/commit/f227b8e730314ab3bd30269d44d24ef6c79383cb):remove all of the unnecessary calls to h2o.init and remove the unnecessary environment variable for version checking during testing



#####R

- [PUBDEV-1744](https://0xdata.atlassian.net/browse/PUBDEV-1744): Improve help message of h2o.init function
- [GitHub](https://github.com/h2oai/h2o-3/commit/887c4bda1219b9d59ddd52604a6d535a01681e94): add valid expression to list of accepted R CMD check outputs.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7762d4a5a184847fd79f2c4f6c190f4aa712f37a): added h2o.anomaly demo to r package  


#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/887be2cdcfef7b8e954950447b295d14c7e30b04): Add -JJ command line argument to allow extra JVM arguments to be passed.
- [GitHub](https://github.com/h2oai/h2o-3/commit/31e5cb6576cd9dd5e738db64f93de5d3f5fe6154): Refactored CSVStream to be more understandable. Fix empty chunk bug.
- [GitHub](https://github.com/h2oai/h2o-3/commit/dbd87534f7f9cd2321f4e646b1171e769845205d): Add hintFlushRemoteChunk to CSVStream.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7b5258f0cfc71310d3105cc1a1bb80952632a073): Add parameterized route for frame export
- [GitHub](https://github.com/h2oai/h2o-3/commit/13709afc274ce0ed7796d63f57dbb2e658b56669): allow string vecs to be toEnum'd (with a sensible cap)
- [GitHub](https://github.com/h2oai/h2o-3/commit/a9ad86bd76a20953005d7eb1981c46849e1a5ad4): allow lists of numbers in reducer ops
- [GitHub](https://github.com/h2oai/h2o-3/commit/5d4eb4d3c96161c92dc5aa4ce84f91a636739778): Add warning message during POJO export if `offset_column` is specified (is not supported)
- [PUBDEV-1853](https://0xdata.atlassian.net/browse/PUBDEV-1853): cleanup: remove addToNavbar from RequestServer [GitHub](https://github.com/h2oai/h2o-3/commit/9362909fbce887c282783da6e9efa9e3a9a9b96c)
- [GitHub](https://github.com/h2oai/h2o-3/commit/9b4f8818eba32abfe4b393d2340df793204abe0d): Add "Open H2O Flow" message.
- [GitHub](https://github.com/h2oai/h2o-3/commit/36b2143bf81d398d6fd8b1b08ad03ae3a33731a7): Code refactoring to allow GBM JUnits to work with H2OApp in multi-node mode. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/3b3bc6fc67237306495433b030833a7f6d3e603f): Replace additive float op by multiplication
- [GitHub](https://github.com/h2oai/h2o-3/commit/70a2e5d859f6c6f9075bdfb93e77aa12c23b1074): Reimplement checksum for Model.Parameters
- [GitHub](https://github.com/h2oai/h2o-3/commit/df6cc628edf2577d05fd0deae68a0d63b04d11c4): Remove debug prints.
- [PUBDEV-1857](https://0xdata.atlassian.net/browse/PUBDEV-1857): cleanup: remove the need for String[] path_params in RequestServer.register() [GitHub](https://github.com/h2oai/h2o-3/commit/5dfca019b1c69c2814911bdfe485fc888525ec99)
- [PUBDEV-1856](https://0xdata.atlassian.net/browse/PUBDEV-1856): cleanup: remove the writeHTML_impl methods from all the schemas
- [PUBDEV-1854](https://0xdata.atlassian.net/browse/PUBDEV-1854): cleanup: make _doc_method optional in the in Route constructors [GitHub](https://github.com/h2oai/h2o-3/commit/a0bd6d7bf065bc78ac34864c1e095ed53dacd5a1)
- [PUBDEV-1858](https://0xdata.atlassian.net/browse/PUBDEV-1858): cleanup: change RequestServer so that only one handler instance is created for each Route




####Bug Fixes 

The following changes are to resolve incorrect software behavior:


#####Algorithms

- [PUBDEV-1674](https://0xdata.atlassian.net/browse/PUBDEV-1674): gbm w gamma: does not seems to split at all; all  trees node pred=0 for attached data [GitHub](https://github.com/h2oai/h2o-3/commit/5796d9e9725bcee27278a31e42c7b77089e65710)
- [PUBDEV-1760](https://0xdata.atlassian.net/browse/PUBDEV-1760): GBM : Deviance testing for exp family
- [PUBDEV-1714](https://0xdata.atlassian.net/browse/PUBDEV-1714): gbm gamma: R vs h2o same split variable, slightly different leaf predictions
- [PUBDEV-1755](https://0xdata.atlassian.net/browse/PUBDEV-1755): DL : Math correctness for Tweedie with Offsets/Weights
- [PUBDEV-1758](https://0xdata.atlassian.net/browse/PUBDEV-1758): DL : Deviance testing for exp family
- [PUBDEV-1756](https://0xdata.atlassian.net/browse/PUBDEV-1756): DL : Math correctness for Poisson with Offsets/Weights
- [PUBDEV-1651](https://0xdata.atlassian.net/browse/PUBDEV-1651): null/residual deviances don't match for various weights cases
- [PUBDEV-1757](https://0xdata.atlassian.net/browse/PUBDEV-1757): DL : Math correctness for Gamma with Offsets/Weights
- [PUBDEV-1680](https://0xdata.atlassian.net/browse/PUBDEV-1680): gbm gamma: seeing train set mse incs after sometime
- [PUBDEV-1724](https://0xdata.atlassian.net/browse/PUBDEV-1724): gbm w tweedie: weird validation error behavior 
- [PUBDEV-1774](https://0xdata.atlassian.net/browse/PUBDEV-1774): setting gbm's balance_classes to True produces suspect models 
- [PUBDEV-1849](https://0xdata.atlassian.net/browse/PUBDEV-1849): K-Means: negative sum-of-squares after mean imputation
- [GitHub](https://github.com/h2oai/h2o-3/commit/09a73ba1d1f5b24b56af842d75259df4ae52af96): Set the iters counter during kmeans center initialization correctly
- [GitHub](https://github.com/h2oai/h2o-3/commit/bfa9cd5179f7b1dce85895db80b28ec9ec743f71): fixed parenthesis in GLM POJO generation
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed0dfe29aab903586a64565a531ecc52b3414dce): Should be updating model each iteration with the newly fitted kmeans clusters, not the old ones!
- [PUBDEV-1867](https://0xdata.atlassian.net/browse/PUBDEV-1867): GLRM with Simplex Fails with Infinite Objective
- [PUBDEV-1666](https://0xdata.atlassian.net/browse/PUBDEV-1666): GBM:Math correctness for Gamma with offsets/weights


#####Python

- [PUBDEV-1779](https://0xdata.atlassian.net/browse/PUBDEV-1779): Fixes intermittent failure seen when Model Metrics were looked at too quickly after a cross validation run.
- [PUBDEV-1409](https://0xdata.atlassian.net/browse/PUBDEV-1409): h2o python h2o.locate() should stop and return "Not found" rather than passing path=None to h2o? causes confusion h2o message  [GitHub](https://github.com/h2oai/h2o-3/commit/c8bdebc4caf0153a721f68963642e0ce92c311ab)
- [PUBDEV-1630](https://0xdata.atlassian.net/browse/PUBDEV-1630): GBM getting intermittent assertion error on iris scoring in `pyunit_weights_api.py`
- [PUBDEV-1770](https://0xdata.atlassian.net/browse/PUBDEV-1770): sigterm caught by python is killing h2o [GitHub](https://github.com/h2oai/h2o-3/commit/f123741c5455fa7e21d6675789fb93ed796f617b)
- [PUBDEV-1409](https://0xdata.atlassian.net/browse/PUBDEV-1409): h2o python h2o.locate() should stop and return "Not found" rather than passing path=None to h2o? causes confusion h2o message
- [HEXDEV-397](https://0xdata.atlassian.net/browse/HEXDEV-397): Python fold_column option requires fold column to be in the training data
- [HEXDEV-394](https://0xdata.atlassian.net/browse/HEXDEV-394): Python client occasionally throws attached error
- [GitHub](https://github.com/h2oai/h2o-3/commit/bce2e56200ba61b34d7ff10986749b94fc836c02): add missing args to kmeans
- [GitHub](https://github.com/h2oai/h2o-3/commit/99ad8f2d7eabd7aedf2f89c725bfaf09527e3cee): add missing kmeans params in
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac26cf2db625a7c26645ee6d4f6cc12f6803fded): add missing checkpoint param
- [PUBDEV-1785](https://0xdata.atlassian.net/browse/PUBDEV-1785): Deadlock while running GBM


#####R

- [PUBDEV-1830](https://0xdata.atlassian.net/browse/PUBDEV-1830): h2o.glm throws an error when `fold_column` and `validation_frame` are both specified
- [PUBDEV-1660](https://0xdata.atlassian.net/browse/PUBDEV-1660): h2oR: when try to get a slice from pca eigenvectors get some formatting error [GitHub](https://github.com/h2oai/h2o-3/commit/8380c9697cb057f2437c8f14deea3a702f810805)
- [GitHub](https://github.com/h2oai/h2o-3/commit/bce4e036ad52d3a4dd75960653f016dc6c076622): fix broken %in% in R
- [PUBDEV-1831](https://0xdata.atlassian.net/browse/PUBDEV-1831): Cross-validation metrics are not displayed in R (and Python?)
- [PUBDEV-1840](https://0xdata.atlassian.net/browse/PUBDEV-1840): Autoencoder model doesn't display properly in R (training metrics) [GitHub](https://github.com/h2oai/h2o-3/commit/5a9880fa00615481da4c1897af3d974c42529e36)

#####System

- [PUBDEV-1790](https://0xdata.atlassian.net/browse/PUBDEV-1790): can't convert iris species column to a character column. 
- [PUBDEV-1520](https://0xdata.atlassian.net/browse/PUBDEV-1520): Kmeans pojo naming inconsistency
- [GitHub](https://github.com/h2oai/h2o-3/commit/f64576a6a981ad2cc9b95e1653949bfbe4bc2de0): fix parse of range ast
- [GitHub](https://github.com/h2oai/h2o-3/commit/30f1fe3396dfc926b6ed959169b0046bbd784164): Sets POJO file name to match the class name. Prior behavior would allow them to be different and give a compile error. 


#####Web UI

- [PUBDEV-1754](https://0xdata.atlassian.net/browse/PUBDEV-1754): Export frame not working in flow : H2OKeyNotFoundArgumentException



---

###Simons (3.0.1.4) - 7/29/15

####New Features

#####Algorithms
- [HEXDEV-220](https://0xdata.atlassian.net/browse/HEXDEV-220): Tweedie distribution for DL
- [HEXDEV-219](https://0xdata.atlassian.net/browse/HEXDEV-219): Poisson distribution for DL
- [HEXDEV-221](https://0xdata.atlassian.net/browse/HEXDEV-221): Gamma distribution for DL
- [PUBDEV-683](https://0xdata.atlassian.net/browse/PUBDEV-683): Enable nfolds for all algos (where reasonable) [GitHub](https://github.com/h2oai/h2o-3/commit/68d74cb438dd535acac18ce8233fdaa25882b6c5)
- [PUBDEV-1791](https://0xdata.atlassian.net/browse/PUBDEV-1791): Add toString() for all models (especially model metrics) [GitHub](https://github.com/h2oai/h2o-3/commit/c253f5ff73b1828de026f69f6846e1b85087b056)
- [GitHub](https://github.com/h2oai/h2o-3/commit/792a0789ef951bf0997251a05cb3dd8d5d92af9e): Enabling model checkpointing for DRF
- [GitHub](https://github.com/h2oai/h2o-3/commit/29b12729465cc4e0c71597616a748ad12ab1a099): Enable checkpointing for GBM.
- [PUBDEV-1698](https://0xdata.atlassian.net/browse/PUBDEV-1698): fold assignment in N-fold cross-validation 


##### Python
- [PUBDEV-386](https://0xdata.atlassian.net/browse/PUBDEV-386): Expose ParseSetup to user in Python
- [PUBDEV-1239](https://0xdata.atlassian.net/browse/PUBDEV-1239): Python: getFrame and getModel missing
- [HEXDEV-334](https://0xdata.atlassian.net/browse/HEXDEV-334): support rbind in python
- [PUBDEV-1215](https://0xdata.atlassian.net/browse/PUBDEV-1215): python to have exportFile calll
- [GitHub](https://github.com/h2oai/h2o-3/commit/bf9cbf96641cbc027e257298cdc67ed6f5bfb065): add cross-validation parameter to metric accessors and respective pyunit
- [PUBDEV-1729](https://0xdata.atlassian.net/browse/PUBDEV-1729): Cross-validation metrics should be shown in R and Python for all models


##### R
- [PUBDEV-385](https://0xdata.atlassian.net/browse/PUBDEV-385): Expose ParseSetup to user in R
- [GitHub](https://github.com/h2oai/h2o-3/commit/d15c0df32a048fbb358ce3daf6968470de9faf6a): add mean residual deviance accessor to R interface 
- [GitHub](https://github.com/h2oai/h2o-3/commit/dd93faa00c7c210aa05225874e608f3a8d9ca5f8): incorporate cross-validation metric access into the R client metric accessors
- [GitHub](https://github.com/h2oai/h2o-3/commit/cf477fb90beeeb901a0a999bdad562c3fa37d818): R interface for checkpointing in RF enabled


#####System

- [PUBDEV-1735](https://0xdata.atlassian.net/browse/PUBDEV-1735): Add 24-MAR-14 06.10.48.000000000 PM style date to autodetected


####Enhancements


#####API 

- [PUBDEV-1451](https://0xdata.atlassian.net/browse/PUBDEV-1451): design for cross-validation APIs [GitHub](https://github.com/h2oai/h2o-3/commit/6ceac99d25b40ca1a14523056e7b48dc4cd0c853)


#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/2c3d06389c9b3fcfeb7df59479653685b5dceb10): Add proper deviance computation for DL regression.
- [GitHub](https://github.com/h2oai/h2o-3/commit/aa5dfeaed742d5e510cbec55faf4668f02086010): Print GLM model details to the logs.
- [GitHub](https://github.com/h2oai/h2o-3/commit/e4b02fc55ba18b05f808d083c75fdcd572ebe88f): Disallow categorical response for GLM with non-binomial family.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0b9aba8cc9cc7e92696dc627e253bd4c47511801): Disallow models with more than 1000 classes, can lead to too large values in DKV due to memory usage of 8*N^2 bytes (the Metrics objects which are in the model output)
- [GitHub](https://github.com/h2oai/h2o-3/commit/cc6384aebc6ef9c7429066f5303c90ce3b551689): DL: Don't train too long in single node mode with auto-tuning.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b108a2b170c4ed4e08d19c1e492316eef8668a0a): Use mean residual deviance to do early stopping in DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/08da7157e0e3fed68a0411b77d19d00666fa34ea): Add a "AUTO" setting for fold_assignment (which is Random). This allows the code to reject non-default user-given values if n-fold CV is not enabled.


#####Python

- [HEXDEV-317](https://0xdata.atlassian.net/browse/HEXDEV-317): Python has to play nicely in a polyglot, long-running environment
- [GitHub](https://github.com/h2oai/h2o-3/commit/16c4b179cd0e35b59c0dd3c2831ab9c1d1f9970b): simplify ast in python frame slicer
- [GitHub](https://github.com/h2oai/h2o-3/commit/7f18d01b9c9a36f85418e7fa79a1a3b4b40a0a9d): add cross validation metrics and mean residual deviance to model show()
- [GitHub](https://github.com/h2oai/h2o-3/commit/93a371ad7a33199c3e962b61f3ed447d25d6adf3): any to take a frame, simplify python's `__contains__`


#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b39364f34d9df926af44da29d0a43d096f9a2c0b): On detaching h2o R package, only shut down H2O instance if it was started by the R client
- [GitHub](https://github.com/h2oai/h2o-3/commit/4db6a89512bd695a3c698b64c3a5d4a298075049): update h2o load

#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/2a926c0950a98eff5a4c06aeaf0373e17176ecd8): Print a handy message (Open H2O Flow in your web browser) when the cluster comes up like Sparkling Water does.
- [GitHub](https://github.com/h2oai/h2o-3/commit/d7cdd52119fd718ae579e2ec9f5229324bb030e5): Replace memory leaky RCurl getURL with curlPerform.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2aa09c4d560e17446b00a568158c1d5164df68df): Add -disable_web parameter.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7c810020a03e5173e8e80f4dd6262d41e60b1651): allow numerics in match
- [GitHub](https://github.com/h2oai/h2o-3/commit/362b79501dbbbddcba98678cf66794a96e06ea14): More refactoring of h2o start. Includes:
  - H2OStarter - a generic class to start H2O. It does all dynamic
    registration
  - H2OTestStarter - a generic class to start h2o-core tests
- [GitHub](https://github.com/h2oai/h2o-3/commit/8dd11df6e61779e731bcdc6a99684a5e69a7df45): Use typed key when it is necessary. Key.make() now returns typed Key<T extends Keyed>. The trick is that type T can be derived by left side of assignment. If it is not possible to derive type of the Key, then developer has to use typed syntax: `Key.<Frame>make("myframe.hex")` The change simplifies Scala code which will be able to derive type key.
- [PUBDEV-1793](https://0xdata.atlassian.net/browse/PUBDEV-1793): Add Job state and start/end time to the model's output [GitHub](https://github.com/h2oai/h2o-3/commit/5ffa988bdd2da4f5a0bc55ef6688d4f63d2c52c7)
- [GitHub](https://github.com/h2oai/h2o-3/commit/10d0f2c30ea3640e5a738bbf4d4adef46817c949): add more places to look when trying to start jar from python's h2o.init
- [GitHub](https://github.com/h2oai/h2o-3/commit/9a28bee6af37ec74c1973aab2ff7d99e257704b5): Cosmetic name changes
- [GitHub](https://github.com/h2oai/h2o-3/commit/c99aed8a1529f71883a73d7c793b10f9bf58df6d): Fetch local node differently from remote node.
- [GitHub](https://github.com/h2oai/h2o-3/commit/9d639ce17a030c2eeaa430ee26b27c416410a20a): Don't clamp node_idx at 0 anymore.
- [GitHub](https://github.com/h2oai/h2o-3/commit/ce677104991836f621c5f5702de476c714aed56d): Added -log_dir option.

 
####Bug Fixes 


#####API

- [PUBDEV-776](https://0xdata.atlassian.net/browse/PUBDEV-776): Schema.parse() needs to be better behaved (like, not crash)


#####Algorithms

- [PUBDEV-1725](https://0xdata.atlassian.net/browse/PUBDEV-1725): pca:glrm -  give bad results for attached data (bec of plus plus initialization)
- [GitHub](https://github.com/h2oai/h2o-3/commit/028688c8eefda072475857c9e282c5888c55f319): Fix deviance calculation, use the sanitized parameters from the model info, where Auto parameter values have been replaced with actual values
- [GitHub](https://github.com/h2oai/h2o-3/commit/13c7700d98acee69508e2585c8400f3877c141dc): Fix offset in DL for exponential family (that doesn't do standardization)
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e89aad34fb0d5589d6a08ff347d95f323005e30): Fix a bug where initial Y was set to all zeroes by kmeans++ when scaling was disabled
- [PUBDEV-1668](https://0xdata.atlassian.net/browse/PUBDEV-1668): GBM: Math correctness for weights
- [PUBDEV-1783](https://0xdata.atlassian.net/browse/PUBDEV-1783): dl: deviance off for large dataset [GitHub](https://github.com/h2oai/h2o-3/commit/8ec0e558ea40b0b4575daff5b3b791040e6c6392)
- [PUBDEV-1667](https://0xdata.atlassian.net/browse/PUBDEV-1667): GBM: Math correctness for Offsets
- [PUBDEV-1778](https://0xdata.atlassian.net/browse/PUBDEV-1778): drf: reporting incorrect mse on validation set [GitHub](https://github.com/h2oai/h2o-3/commit/dad2c4ba5902d30122479d6fffa3bda76f8e1cec)
- [GitHub](https://github.com/h2oai/h2o-3/commit/e08a2480e8b44bbfaf6f700792da6288d1188320): Fix DRF scoring with 0 trees.

##### Python

- [PUBDEV-1260](https://0xdata.atlassian.net/browse/PUBDEV-1260): Python: Requires asnumeric() function
- [GitHub](https://github.com/h2oai/h2o-3/commit/a665a883dd41319941fa73599ac11559eeab0c3c): python interface: add folds_column to x, if it doesn't already exist in x
- [PUBDEV-1763](https://0xdata.atlassian.net/browse/PUBDEV-1763): Python : Math correctness tests for Tweedie/Gamma/Possion with offsets/weights
- [PUBDEV-1762](https://0xdata.atlassian.net/browse/PUBDEV-1762): Python : Deviance tests for all algos in python [GitHub](https://github.com/h2oai/h2o-3/commit/c9641faa4f2bdfbef850e6eb15a2195e157da0af)
- [PUBDEV-1671](https://0xdata.atlassian.net/browse/PUBDEV-1671): intermittent: pyunit_weights_api.py, hex.tree.SharedTree$ScoreBuildOneTree@645acd60java.lang.AssertionError    at hex.tree.DRealHistogram.scoreMSE(DRealHistogram.java:118), iris dataset [GitHub](https://github.com/h2oai/h2o-3/commit/cf886a78d3c34a8ef34f1e5149b4208281a927da)

##### R
- [PUBDEV-1257](https://0xdata.atlassian.net/browse/PUBDEV-1257): R: no is.numeric method for H2O objects
- [PUBDEV-1622](https://0xdata.atlassian.net/browse/PUBDEV-1622): NPE in water.api.RequestServer, water.util.RString.replace(RString.java:132)...got flagged as WARN in log...I would think we should have all NPE's be ERROR / fatal? or ?? [GitHub](https://github.com/h2oai/h2o-3/commit/49b68c06c9c85b737f4c2e98a7d5f8d4815da1f5)
- [PUBDEV-1655](https://0xdata.atlassian.net/browse/PUBDEV-1655): h2o.strsplit needs isNA check
- [PUBDEV-1084](https://0xdata.atlassian.net/browse/PUBDEV-1084): h2o.setTimezone NPE
- [PUBDEV-1738](https://0xdata.atlassian.net/browse/PUBDEV-1738): R: cloud name creation can't handle user names with spaces

#####System

- [PUBDEV-1410](https://0xdata.atlassian.net/browse/PUBDEV-1410): apply causes assert errors mentioning deadlock in runit_small_client_mode ...build never completes after hours ..deadlock?
- [PUBDEV-1195](https://0xdata.atlassian.net/browse/PUBDEV-1195): docker build fails
- [HEXDEV-362](https://0xdata.atlassian.net/browse/HEXDEV-362): Bug in /parsesetup data preview [GitHub](https://github.com/h2oai/h2o-3/commit/ee0b787ac50cbe747622adaec2344c243834f035)
- [PUBDEV-1766](https://0xdata.atlassian.net/browse/PUBDEV-1766): H2O xval: when delete all models: get Error evaluating future[6] :Error calling DELETE /3/Models/gbm_cv_13  
- [PUBDEV-1767](https://0xdata.atlassian.net/browse/PUBDEV-1767): H2O: when list frames after removing most frames, get: roll ups not possible vec deleted error [GitHub](https://github.com/h2oai/h2o-3/commit/7cf1212e19b1f9ab8071718fb31d1565f7942fef)


#####Web UI

- [PUBDEV-1782](https://0xdata.atlassian.net/browse/PUBDEV-1782): Flow: View Data fails when there is a UUID column (and maybe also a String column)
- [PUBDEV-1769](https://0xdata.atlassian.net/browse/PUBDEV-1769): xval: cancel job does not work [GitHub](https://github.com/h2oai/h2o-3/commit/d05fc8d818d3ec5c3ea6f28f4c791a2d8d0871fc)


---

###Simons (3.0.1.3) - 7/24/15

####New Features

#####Python

- [PUBDEV-1734](https://0xdata.atlassian.net/browse/PUBDEV-1734): Add save and load model to python api
- [PUBDEV-1314](https://0xdata.atlassian.net/browse/PUBDEV-1314): Python needs "str" operator, like R's
- [GitHub](https://github.com/h2oai/h2o-3/commit/df27d5c46011647ddb6363431ca085cc4dba37a5): turn on `H2OFrame __repr__`

####Enhancements

#####API 

- [GitHub](https://github.com/h2oai/h2o-3/commit/d22b508c215fb6033ed11fe5009de744bd38f2d7): Increase sleep from 2 to 3 because h2o itself does a sleep 2 on the REST API before triggering the shutdown.

#####System

- [PUBDEV-1730](https://0xdata.atlassian.net/browse/PUBDEV-1730): Make export file a  job [GitHub](https://github.com/h2oai/h2o-3/commit/31cdef5b6a48f11b6568e0131fcb4e0acb06f5ad)


####Bug Fixes 

The following changes are to resolve incorrect software behavior:

#####Algorithms

- [PUBDEV-1743](https://0xdata.atlassian.net/browse/PUBDEV-1743): gbm poisson w weights: deviance off 
- [PUBDEV-1736](https://0xdata.atlassian.net/browse/PUBDEV-1736): gbm poisson with offset: seems to be giving wrong leaf predictions

#####Python

- [PUBDEV-1731](https://0xdata.atlassian.net/browse/PUBDEV-1731): Python `get_frame()` results in deleting a frame created by Flow
- [HEXDEV-389](https://0xdata.atlassian.net/browse/HEXDEV-389): Split frame from python 
- [HEXDEV-388](https://0xdata.atlassian.net/browse/HEXDEV-388): python client H2OFrame constructor puts the header into the data (as the first row)

#####R

- [PUBDEV-1504](https://0xdata.atlassian.net/browse/PUBDEV-1504): Runit intermittent fails : runit_pub_180_ddply.R 
- [PUBDEV-1678](https://0xdata.atlassian.net/browse/PUBDEV-1678): Client mode jobs fail on runit_hex_1750_strongRules_mem.R

#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/9f83f68ffc70f164de51188c4837345a6a12ac13): Model parameters should be always public.

###Simons (3.0.1.1) - 7/20/15

####New Features

##### Algorithms

- [HEXDEV-213](https://0xdata.atlassian.net/browse/HEXDEV-213): Tweedie distributions for GBM [GitHub](https://github.com/h2oai/h2o-3/commit/a5892087d08bcee9b8c017bd6173601d262d9f79)
- [HEXDEV-212](https://0xdata.atlassian.net/browse/HEXDEV-212): Poisson distributions for GBM [GitHub](https://github.com/h2oai/h2o-3/commit/861322058519cc3455e924449cbe7dfdecf67514)
- [PUBDEV-1115](https://0xdata.atlassian.net/browse/PUBDEV-1115): properly test PCA and mark it non-experimental

#####Python

- [PUBDEV-1437](https://0xdata.atlassian.net/browse/PUBDEV-1437): Python needs "nlevels" operator like R
- [PUBDEV-1434](https://0xdata.atlassian.net/browse/PUBDEV-1434): Python needs "levels" operator, like R
- [PUBDEV-1355](https://0xdata.atlassian.net/browse/PUBDEV-1355): Python needs h2o.trim, like in R
- [PUBDEV-1354](https://0xdata.atlassian.net/browse/PUBDEV-1354): Python needs h2o.toupper, like in R
- [PUBDEV-1352](https://0xdata.atlassian.net/browse/PUBDEV-1352): Python needs h2o.tolower, like in R
- [PUBDEV-1350](https://0xdata.atlassian.net/browse/PUBDEV-1350): Python needs h2o.strsplit, like in R
- [PUBDEV-1347](https://0xdata.atlassian.net/browse/PUBDEV-1347): Python needs h2o.shutdown, like in R
- [PUBDEV-1343](https://0xdata.atlassian.net/browse/PUBDEV-1343): Python needs h2o.rep_len, like in R
- [PUBDEV-1340](https://0xdata.atlassian.net/browse/PUBDEV-1340): Python needs h2o.nlevels, like in R
- [PUBDEV-1338](https://0xdata.atlassian.net/browse/PUBDEV-1338): Python needs h2o.ls, like in R
- [PUBDEV-1344](https://0xdata.atlassian.net/browse/PUBDEV-1344): Python needs h2o.saveModel, like in R
- [PUBDEV-1337](https://0xdata.atlassian.net/browse/PUBDEV-1337): Python needs h2o.loadModel, like in R
- [PUBDEV-1335](https://0xdata.atlassian.net/browse/PUBDEV-1335): Python needs h2o.interaction, like in R
- [PUBDEV-1334](https://0xdata.atlassian.net/browse/PUBDEV-1334): Python needs h2o.hist, like in R
- [PUBDEV-1351](https://0xdata.atlassian.net/browse/PUBDEV-1351): Python needs h2o.sub, like in R
- [PUBDEV-1333](https://0xdata.atlassian.net/browse/PUBDEV-1333): Python needs h2o.gsub, like in R
- [PUBDEV-1336](https://0xdata.atlassian.net/browse/PUBDEV-1336): Python needs h2o.listTimezones, like in R
- [PUBDEV-1346](https://0xdata.atlassian.net/browse/PUBDEV-1346): Python needs h2o.setTimezone, like in R
- [PUBDEV-1332](https://0xdata.atlassian.net/browse/PUBDEV-1332): Python needs h2o.getTimezone, like in R
- [PUBDEV-1329](https://0xdata.atlassian.net/browse/PUBDEV-1329): Python needs h2o.downloadCSV, like in R
- [PUBDEV-1328](https://0xdata.atlassian.net/browse/PUBDEV-1328): Python needs h2o.downloadAllLogs, like in R
- [PUBDEV-1327](https://0xdata.atlassian.net/browse/PUBDEV-1327): Python needs h2o.createFrame, like in R
- [PUBDEV-1326](https://0xdata.atlassian.net/browse/PUBDEV-1326): Python needs h2o.clusterStatus, like in R
- [PUBDEV-1323](https://0xdata.atlassian.net/browse/PUBDEV-1323): Python needs svd algo
- [PUBDEV-1322](https://0xdata.atlassian.net/browse/PUBDEV-1322): Python needs prcomp algo
- [PUBDEV-1321](https://0xdata.atlassian.net/browse/PUBDEV-1321): Python needs naiveBayes algo
- [PUBDEV-1320](https://0xdata.atlassian.net/browse/PUBDEV-1320): Python needs model num_iterations accessor for clustering models, like R's
- [PUBDEV-1318](https://0xdata.atlassian.net/browse/PUBDEV-1318): Python needs screeplot and plot methods, like R's. (should probably check for matplotlib)
- [PUBDEV-1317](https://0xdata.atlassian.net/browse/PUBDEV-1317): Python needs multinomial model hit_ratio_table accessor, like R's
- [PUBDEV-1316](https://0xdata.atlassian.net/browse/PUBDEV-1316): Python needs model scoreHistory accessor, like R's
- [PUBDEV-1315](https://0xdata.atlassian.net/browse/PUBDEV-1315): R needs weights and biases accessors for deeplearning models
- [PUBDEV-1313](https://0xdata.atlassian.net/browse/PUBDEV-1313): Python needs "as.Date" operator, like R's
- [PUBDEV-1312](https://0xdata.atlassian.net/browse/PUBDEV-1312): Python needs "rbind" operator, like R's
- [PUBDEV-1345](https://0xdata.atlassian.net/browse/PUBDEV-1345): Python needs h2o.setLevel and h2o.setLevels, like in R
- [PUBDEV-1311](https://0xdata.atlassian.net/browse/PUBDEV-1311): Python needs "setLevel" operator, like R's
- [PUBDEV-1306](https://0xdata.atlassian.net/browse/PUBDEV-1306): Python needs "anyFactor" operator, like R's
- [PUBDEV-1305](https://0xdata.atlassian.net/browse/PUBDEV-1305): Python needs "table" operator, like R's
- [PUBDEV-1301](https://0xdata.atlassian.net/browse/PUBDEV-1301): Python needs "as.numeric" operator, like R's
- [PUBDEV-1300](https://0xdata.atlassian.net/browse/PUBDEV-1300): Python needs "as.character" operator, like R's
- [PUBDEV-1293](https://0xdata.atlassian.net/browse/PUBDEV-1293): Python needs "signif" operator, like R's
- [PUBDEV-1292](https://0xdata.atlassian.net/browse/PUBDEV-1292): Python needs "round" operator, like R's
- [PUBDEV-1291](https://0xdata.atlassian.net/browse/PUBDEV-1291): Python need transpose operator, like R's t operator
- [PUBDEV-1289](https://0xdata.atlassian.net/browse/PUBDEV-1289): Python needs element-wise division and multiplication operators, like %/% and %-%in R
- [PUBDEV-1330](https://0xdata.atlassian.net/browse/PUBDEV-1330): Python needs h2o.exportHDFS, like in R
- [PUBDEV-1357](https://0xdata.atlassian.net/browse/PUBDEV-1357): Python and R need which operator [GitHub](https://github.com/h2oai/h2o-3/commit/a39de4dce02e5516279f29cc6f1933a8bc2c5562)
- [PUBDEV-1356](https://0xdata.atlassian.net/browse/PUBDEV-1356): Python and R needs isnumeric and ischaracter operators
- [PUBDEV-1342](https://0xdata.atlassian.net/browse/PUBDEV-1342): Python needs h2o.removeVecs, like in R
- [PUBDEV-1324](https://0xdata.atlassian.net/browse/PUBDEV-1324): Python needs h2o.assign, like in R [GitHub](https://github.com/h2oai/h2o-3/commit/44faa7f15801b9218db6dfa84cde85baa56afb62)
- [PUBDEV-1296](https://0xdata.atlassian.net/browse/PUBDEV-1296): Python and R h2o clients need "any" operator, like R's
- [PUBDEV-1295](https://0xdata.atlassian.net/browse/PUBDEV-1295): Python and R h2o clients need "prod" operator, like R's
- [PUBDEV-1294](https://0xdata.atlassian.net/browse/PUBDEV-1294): Python and R h2o clients need "range" operator, like R's
- [PUBDEV-1290](https://0xdata.atlassian.net/browse/PUBDEV-1290): Python and R h2o clients need "cummax", "cummin", "cumprod", and "cumsum" operators, like R's
- [PUBDEV-1325](https://0xdata.atlassian.net/browse/PUBDEV-1325): Python needs h2o.clearLog, like in R
- [PUBDEV-1349](https://0xdata.atlassian.net/browse/PUBDEV-1349): Python needs h2o.startLogging and h2o.stopLogging, like in R
- [PUBDEV-1341](https://0xdata.atlassian.net/browse/PUBDEV-1341): Python needs h2o.openLog, like in R
- [PUBDEV-1348](https://0xdata.atlassian.net/browse/PUBDEV-1348): Python needs h2o.startGLMJob, like in R
- [PUBDEV-1331](https://0xdata.atlassian.net/browse/PUBDEV-1331): Python needs h2o.getFutureModel, like in R
- [PUBDEV-1302](https://0xdata.atlassian.net/browse/PUBDEV-1302): Python needs "match" operator, like R's
- [PUBDEV-1298](https://0xdata.atlassian.net/browse/PUBDEV-1298): Python needs "%in%" operator, like R's
- [PUBDEV-1310](https://0xdata.atlassian.net/browse/PUBDEV-1310): Python needs "scale" operator, like R's
- [PUBDEV-1297](https://0xdata.atlassian.net/browse/PUBDEV-1297): Python needs "all" operator, like R's
- [GitHub](https://github.com/h2oai/h2o-3/commit/fbe17d13d5dfe258ff7c62def3e4e3869a5d25d5): add start_glm_job() and get_future_model() to python client. add H2OModelFuture class. add respective pyunit

##### R

- [PUBDEV-1273](https://0xdata.atlassian.net/browse/PUBDEV-1273): Add h2oEnsemble R package to h2o-3
- [PUBDEV-1319](https://0xdata.atlassian.net/browse/PUBDEV-1319): R needs centroid_stats accessor like Python, for clustering models

#####Rapids

- [PUBDEV-1635](https://0xdata.atlassian.net/browse/PUBDEV-1635): the equivalent of R's "any" should probably implemented in rapids
- [PUBDEV-1634](https://0xdata.atlassian.net/browse/PUBDEV-1634): the equivalent of R's cummin, cummax, cumprod, cumsum should probably implemented in rapids
- [PUBDEV-1633](https://0xdata.atlassian.net/browse/PUBDEV-1633): the equivalent of R's "range" should probably implemented in rapids
- [PUBDEV-1632](https://0xdata.atlassian.net/browse/PUBDEV-1632): the equivalent of R's "prod" should probably implemented in rapids
- [PUBDEV-1699](https://0xdata.atlassian.net/browse/PUBDEV-1699): the equivalent of R's "unique" should probably implemented in rapids [GitHub](https://github.com/h2oai/h2o-3/commit/713b27f1c0ec4f879f3f39146acb2f888fd27d40)


#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/f738707830052bfa499d83ff91c29d2e7d13e113): changed to new AMI
- [PUBDEV-679](https://0xdata.atlassian.net/browse/PUBDEV-679): Create cross-validation holdout sets using the per-row weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/3c7f296804d72b9b6940aaccc63f329383ab01fb): Add user_name. Add ExtensionHandler1.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0ddf0740ad2e13a2a45137b4d017775900066244): Added auth options to h2o.init(). 
- [GitHub](https://github.com/h2oai/h2o-3/commit/0f9f71335e16a2632c3072782143f47657883129): Added H2O.calcNextUniqueModelId().
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc059e063205fcf051f474f8d97ff3ffd90ee066): Add ldap arg.

##### Web UI

- [HEXDEV-231](https://0xdata.atlassian.net/browse/HEXDEV-231): Flow: Ability to change column type post-Parse

####Enhancements

#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/b2d289b377dc7535344d40a36fdc47f5138545cf): use fixed seed to avoid bad splits with some seeds
- [GitHub](https://github.com/h2oai/h2o-3/commit/643cdce000d7d372bff6a1511d9bcd6695ddcf0d): Change seed to avoid type flip from integer to double after row slicing, which leads to different split decisions
- [GitHub](https://github.com/h2oai/h2o-3/commit/1fad4e3f0b8e30ffa9138d4adb9be645ff20d74b): Add option during kmeans scoring to return matrix of indicator columns for cluster assignment, which is necessary for initializing GLRM
- [GitHub](https://github.com/h2oai/h2o-3/commit/763ea02e1bc6e30f5e1a56437c0f374ac59e67a1): Output number of processed observations in PCA
- [GitHub](https://github.com/h2oai/h2o-3/commit/7d13f34e6dcd4a09871bd3c19d9724e2d2d80660): Add validation into PCA with GramSVD
- [GitHub](https://github.com/h2oai/h2o-3/commit/84ae7075b22fb1f480e7076cc1c276a41969043c): Code cleanup of distributions. Also rename _n_folds -> _nfolds for consistency 
- [GitHub](https://github.com/h2oai/h2o-3/commit/a6716f9ab503d653189c328a287aaf5213f6d737): Remove restriction to data frames with more than 1 column
- [GitHub](https://github.com/h2oai/h2o-3/commit/650b599938945bb91ca07bbb860207709a394564): Add debugging output for DL auto-tuning.
- [PUBDEV-556](https://0xdata.atlassian.net/browse/PUBDEV-556): implement algo-agnostic cross-validation mechanism via a column of weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/8001b5336960629b738da9df36261ca6c538e760): When initializing with kmeans++ set X to matrix of indicator columns corresponding to cluster assignments, unless closed form solution exists
- [GitHub](https://github.com/h2oai/h2o-3/commit/8c8fc92eb4a36cee5751597430687729f3527c60): Always print DL auto-tuning info for now.
- [PUBDEV-1657](https://0xdata.atlassian.net/browse/PUBDEV-1657): pca: would be good to remove the redundant std dev from flow pca model object

#####API

- [GitHub](https://github.com/h2oai/h2o-3/commit/eb68b384ff43a94f6dd0468b2bc4c67de6c23350): Set Content-Type: application/x-www-form-urlencoded for regular POST requests. 
- [HEXDEV-272](https://0xdata.atlassian.net/browse/HEXDEV-272): Move `response_column` parameter above `ignored_columns` parameter [GitHub](https://github.com/h2oai/h2o-3/commit/522b45f1339eefc21b7b0a76e1d42a6cc77bcc00)
	- All of the fields of a schema are now stored in the leaf child of the class hierarchy. Changed the implementation of fields() to simply return the fields variable of a schema. The function calls `H2O.fail()` if it attempts to access a field from a non-leaf child. `response_column` is now moved above `ignored_columns` for every applicable schema. 'own_fields' is also now renamed to 'fields'
- [GitHub](https://github.com/h2oai/h2o-3/commit/11ae769255c2502ecb1ae7438752b2449210b580): Don't use features from servlet api 3.0 or later anymore. Instead save the response status in a thread local variable and fish it out when needed.	

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/1e5ec4a3fe89979e634f080d3e2e96eb2bcec64c): don't use the header of the timezone table for a choice
- [GitHub](https://github.com/h2oai/h2o-3/commit/e40e0c68e2b5b4e2c0cbc68e0a43490c6847416a): never delete models. ever.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0ef4c0d9ec9d9ba62d9603ea0aadaec9a5b50842): add na_rm argument
- [GitHub](https://github.com/h2oai/h2o-3/commit/76227f4c38fc3e3e8d57b3bbd585ae04901d119b): add prod to python interface

#####System 

- [GitHub](https://github.com/h2oai/h2o-3/commit/b2c6486fe67c9e4042330e83a8e1475d29217082): use Key instead of Vec in refcnter
- [GitHub](https://github.com/h2oai/h2o-3/commit/a940c528b6cb4db6fdd087c15077d42584ae179a): protect vecs in apply
- [GitHub](https://github.com/h2oai/h2o-3/commit/34d3d92feeec72e3122661393a839388afcb2a6c): Allows for more than one column to remain unnamed. The new naming will fill in the blanks. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc4f64e2f6b43a60665f711b758d7874e25f34af): Refactoring of hadoop mapper and driver.
- [GitHub](https://github.com/h2oai/h2o-3/commit/49c8c767caf1fce2b373977019c758570ee34959): Remove -hdfs option.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2640b20b5a3567d65faed9b1090a6f846711ab23): Adds more checks for a parse cancel at more stages during the post ingestion file parse. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/29db2e5655bf29e2065dc95267588fb23d583293): Refactor method name for clarification.
- [GitHub](https://github.com/h2oai/h2o-3/commit/7ae285df8f448c248219784b9b0d16d513850e6f): Cleans up and comments the freeing of chunks from a parsed file.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8b0f30fefb573273675f829dfb24073aa22540a3): Since more startup logic is getting added, simplify H2OClientApp as much as possible. Remove H2OClient entirely. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/100fbf07ff9874aebe620e962721e8d2c547cb1a): Add dedicated AddCommonResponseHeadersHandler handler to set common response headers up-front. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/1af5a632d5eafc56df1bb451a9f94ac21cb5a357): More refactoring of startup. Pushed a bunch of code from H2OApp into H2O. Added H2O.configureLogging(). 
- [GitHub](https://github.com/h2oai/h2o-3/commit/0047eea9b28d1842f8326917eafd41fcd655c988): Make Progress extend Keyed.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4a0eda9a4fd798367a5133894459b7d165f89232): Make createServer() protected.
- [GitHub](https://github.com/h2oai/h2o-3/commit/2428fa3a6bc8178d5dfde323438acb29cc12a032): model_id should probably be a Key<Model>, not Key<Frame>.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1778487a41aa4ccc76b97c437fc1b7625784116a): Change Jetty version from 9 to 8 to get Java 6 compatibility back.

#####Web UI

- [PUBDEV-1521](https://0xdata.atlassian.net/browse/PUBDEV-1521): show REST API and overall UI response times for each cell in Flow
- [HEXDEV-304](https://0xdata.atlassian.net/browse/HEXDEV-304): Flow: Emphasize run time in job-progress output
- [PUBDEV-1522](https://0xdata.atlassian.net/browse/PUBDEV-1522): show wall-clock start and run times in the Flow outline
- [PUBDEV-1707](https://0xdata.atlassian.net/browse/PUBDEV-1707): Hook up "Export" button for datasets (frames) in Flow.


####Bug Fixes 

#####Algorithms

- [PUBDEV-1641](https://0xdata.atlassian.net/browse/PUBDEV-1641): gbm w poisson: get  java.lang.AssertionError' at hex.tree.gbm.GBM$GBMDriver.buildNextKTrees on attached data
- [PUBDEV-1672](https://0xdata.atlassian.net/browse/PUBDEV-1672): kmeans: get AIOOB with user specified centroids [GitHub](https://github.com/h2oai/h2o-3/commit/231e33b42b5408ec4e664f7f614a8f37aabbab10)
	-  Throw an error if the number of rows in the user-specified initial centers is not equal to k.
- [PUBDEV-1654](https://0xdata.atlassian.net/browse/PUBDEV-1654): pca: gram-svd std dev differs for v2 vs v3 for attached data 
- [GitHub](https://github.com/h2oai/h2o-3/commit/42831143c9b208596fa60f3d8f86c5bd1109ec64): Fix DL
- [GitHub](https://github.com/h2oai/h2o-3/commit/19794673a5e2a5cf4b5f5d550f4184266ae8799a): Fix a bug in PCA utilities for k = 1
- [PUBDEV-1700](https://0xdata.atlassian.net/browse/PUBDEV-1700): nfolds: flow-when set nfold =1 job hangs  for ever; in terminal get java.lang.AssertionError
- [PUBDEV-1706](https://0xdata.atlassian.net/browse/PUBDEV-1706): GBM/DRF: is balance_classes=TRUE and nfolds>1 valid? [GitHub](https://github.com/h2oai/h2o-3/commit/5f82d3b5f24f11f3a62823b139bd3dd0f44f6c44)
- [PUBDEV-806](https://0xdata.atlassian.net/browse/PUBDEV-806): GLM => `runit_demo_glm_uuid.R` : water.exceptions.H2OIllegalArgumentException
- [PUBDEV-1696](https://0xdata.atlassian.net/browse/PUBDEV-1696): Client (model-build) is blocked when passing illegal nfolds value. [GitHub](https://github.com/h2oai/h2o-3/commit/456fe73a8b120351fbfd5e3a21963439c00cd630)
- [PUBDEV-1690](https://0xdata.atlassian.net/browse/PUBDEV-1690): Cross Validation: if nfolds > number of observations, should it default to leave-one-out cross-validation?
- [PUBDEV-1537](https://0xdata.atlassian.net/browse/PUBDEV-1537): pca: on airlines get  java.lang.AssertionError at hex.svd.SVD$SVDDriver.compute2(SVD.java:219) [GitHub](https://github.com/h2oai/h2o-3/commit/0923c60d47cd089bda1163feeb425e3f2d7e586c)
- [PUBDEV-1603](https://0xdata.atlassian.net/browse/PUBDEV-1603): pca: glrm giving very different std dev than R and h2o's other methods for attached data
- [GitHub](https://github.com/h2oai/h2o-3/commit/0f38e2f0c732095b005dc75cbb0b5b6c04c0b031): Fix a potential race condition in tree validation scoring.
- [GitHub](https://github.com/h2oai/h2o-3/commit/6cfbfdfac28205ba99f3911eaf39ab154fc1cd76): Fix GLM parameter schema. Clean up hasOffset() and hasWeights()


##### Python

- [PUBDEV-1627](https://0xdata.atlassian.net/browse/PUBDEV-1627): column name missing (python client)
- [PUBDEV-1629](https://0xdata.atlassian.net/browse/PUBDEV-1629): python client's tail() header incorrect [GitHub](https://github.com/h2oai/h2o-3/commit/a5055880e9f2f527e99a9811e695170c5c5e00dc)
- [PUBDEV-1413](https://0xdata.atlassian.net/browse/PUBDEV-1413): intermittent assertion errors in `pyunit_citi_bike_small.py/pyunit_citi_bike_large.py`. Client apparently not notified
- [PUBDEV-1590](https://0xdata.atlassian.net/browse/PUBDEV-1590): "Trying to unlock null" assertion during `pyunit_citi_bike_large.py`
- [PUBDEV-1400](https://0xdata.atlassian.net/browse/PUBDEV-1400): match operator should take numerics

#####R

- [PUBDEV-1663](https://0xdata.atlassian.net/browse/PUBDEV-1663): R CMD Check failures [GitHub](https://github.com/h2oai/h2o-3/commit/d707fa0b56c9bc8d8e43861f5c690c1e8aaad809) 
- [PUBDEV-1695](https://0xdata.atlassian.net/browse/PUBDEV-1695): R CMD Check failing on running examples [GitHub](https://github.com/h2oai/h2o-3/commit/b650fb588a3c9d8e8e524db4154a0fd72112fec6)
- [PUBDEV-1721](https://0xdata.atlassian.net/browse/PUBDEV-1721): R: group_by causes h2o to hang on multinode cluster
- [PUBDEV-1501](https://0xdata.atlassian.net/browse/PUBDEV-1501): Python and R h2o clients need "unique" operator, like R's [GitHub - R](https://github.com/h2oai/h2o-3/commit/90423fa68058d68524efb2306fe5a7b272ccd964) [GitHub - Python](https://github.com/h2oai/h2o-3/commit/a53f6913f3431732f6c854b7fc3b15c3ce11171b)
- [PUBDEV-1711](https://0xdata.atlassian.net/browse/PUBDEV-1711): is.numeric in R interface faulty [GitHub](https://github.com/h2oai/h2o-3/commit/fbb44071f7cd19b43a7ac40a8a0652d2010363ea)
- [PUBDEV-1719](https://0xdata.atlassian.net/browse/PUBDEV-1719): Intermittent: `runit_deeplearning_autoencoder_large.R` : gets wrong answer?
- [PUBDEV-1688](https://0xdata.atlassian.net/browse/PUBDEV-1688): 2 nfolds tests fail intermittently: `runit_RF_iris_nfolds.R` and `runit_GBM_cv_nfolds.R` [GitHub](https://github.com/h2oai/h2o-3/commit/9095d1e5d52de27e0622f7ac309d1afbcf09aefb)
- [PUBDEV-1718](https://0xdata.atlassian.net/browse/PUBDEV-1718): Intermittent: `runit_deeplearning_anomaly_large.R `: training slows down to 0 samples/ sec [GitHub](https://github.com/h2oai/h2o-3/commit/d43d47014ff812744c2522dad27246a5e6014738)

#####Rapids

- [PUBDEV-1713](https://0xdata.atlassian.net/browse/PUBDEV-1713): Rapids ASTAll faulty [GitHub](https://github.com/h2oai/h2o-3/commit/3f9e71ef6ad8251b1854a7c05a9945bc629df327)


##### Sparkling Water

- [PUBDEV-1562](https://0xdata.atlassian.net/browse/PUBDEV-1562): Migration to Spark 1.4

##### System

- [PUBDEV-1551](https://0xdata.atlassian.net/browse/PUBDEV-1551): Parser: Multifile Parse fails with 0-byte files in directory [GitHub](https://github.com/h2oai/h2o-3/commit/e95f0e4d20c2281e61009349c63e73520fcf30a2)
- [HEXDEV-325](https://0xdata.atlassian.net/browse/HEXDEV-325): Empty reply when parsing dataset with mismatching header and data column length
- [PUBDEV-1509](https://0xdata.atlassian.net/browse/PUBDEV-1509): Split frame : Big datasets : On 186K rows 3200 Cols split frame took 40 mins => which is too long
- [PUBDEV-1438](https://0xdata.atlassian.net/browse/PUBDEV-1438): Column naming can create duplicate column names
- [PUBDEV-1105](https://0xdata.atlassian.net/browse/PUBDEV-1105): NPE in Rollupstats after failed parse
- [PUBDEV-1142](https://0xdata.atlassian.net/browse/PUBDEV-1142): H2O parse: When cancel a parse job, key remains locked and hence unable to delete the file [GitHub](https://github.com/h2oai/h2o-3/commit/c2a110fb0a44173eb8549acff7ec51a9a23b64ad)
- [GitHub](https://github.com/h2oai/h2o-3/commit/44af14bdd8b249943dd72405090515f425c3b720): client mode deadlock issue resolution
- [PUBDEV-1670](https://0xdata.atlassian.net/browse/PUBDEV-1670): Client mode fails consistently sometimes : `GBM_offset_tweedie.R.out.txt`  :
- [GitHub](https://github.com/h2oai/h2o-3/commit/efb80e43867276dd6b4f64fc3cc7b3978383627a): nbhm bug: K == TOMBSTONE not key == TOMBSTONE
- [GitHub](https://github.com/h2oai/h2o-3/commit/6240c43a88f5a5aae92eb71c5bc1e568bc791977): Pulls out a GAID from resource in jar if the GAID doesn't equal the default. Presumably the GAID has been changed by the jar baking program.

##### Web UI

- [PUBDEV-872](https://0xdata.atlassian.net/browse/PUBDEV-872): Flows : Not able to load saved flows from hdfs/local [GitHub](https://github.com/h2oai/h2o-3/commit/4ea4ffe400512636919964b901e38581c65a68b7)
- [PUBDEV-554](https://0xdata.atlassian.net/browse/PUBDEV-554): Flow:Parse two different files simultaneously, flow should either complain or fill the additional (incompatible) rows with nas 
- [PUBDEV-1527](https://0xdata.atlassian.net/browse/PUBDEV-1527): missing .java extension when downloading pojo [GitHub](https://github.com/h2oai/h2o-3/commit/c41e81d4b5daa1d214aeb6695c1095c72e8ada85)
- [PUBDEV-1642](https://0xdata.atlassian.net/browse/PUBDEV-1642): Changing columns type takes column list back to first page of columns
- [PUBDEV-1508](https://0xdata.atlassian.net/browse/PUBDEV-1508): Flow : Import file => Parse => Error compiling coffee-script Maximum call stack size exceeded
- [PUBDEV-1606](https://0xdata.atlassian.net/browse/PUBDEV-1606): Flow :=> Cannot save flow on hdfs
- [PUBDEV-1527](https://0xdata.atlassian.net/browse/PUBDEV-1527): missing .java extension when downloading pojo
- [PUBDEV-1653](https://0xdata.atlassian.net/browse/PUBDEV-1653): Flow: the column names do not modify when user changes the dataset in model builder

---

###Shannon (3.0.0.26) - 7/4/15

####New Features

#####Algorithms

- [PUBDEV-1592](https://0xdata.atlassian.net/browse/PUBDEV-1592): Expose standardization shift/mult values in the Model output in R/Python. [GitHub](https://github.com/h2oai/h2o-3/commit/af6fb8fa9ca2d75bf45fb5eb130720a76f5ed324)


#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/fd19d6b21a35338c481455f3ca0974cc98c4957d): add h2o.shutdown to python client
- [GitHub](https://github.com/h2oai/h2o-3/commit/ce3a94cba9c00ae6beb9e45870fad9c7e0dbb575): add h2o.hist and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/ea8073d78276da011ed525f4654472d23e94e5cb): gbm weight pyunit (variable importances)

#####R

- [HEXDEV-375](https://0xdata.atlassian.net/browse/HEXDEV-375): Github home for R demos


#####Web UI

- [PUBDEV-203](https://0xdata.atlassian.net/browse/PUBDEV-203): Change data type in flow
- [PUBDEV-1277](https://0xdata.atlassian.net/browse/PUBDEV-1277): Flow needs as.factor and as.numeric after parse


####Enhancements

#####Algorithms

- [PUBDEV-1494](https://0xdata.atlassian.net/browse/PUBDEV-1494): GBM : Weights math correctness tests in R
- [PUBDEV-1523](https://0xdata.atlassian.net/browse/PUBDEV-1523): GLM w tweedie: for attached data, R giving much better res dev than h2o 
- [PUBDEV-1396](https://0xdata.atlassian.net/browse/PUBDEV-1396): Offsets/Weights: Math correctness for GLM
- [PUBDEV-1496](https://0xdata.atlassian.net/browse/PUBDEV-1496): RF : Weights Math correctness tests in R
- [HEXDEV-366](https://0xdata.atlassian.net/browse/HEXDEV-366): remove weights option from DRF and GBM in REST API, Python, R
- [PUBDEV-1553](https://0xdata.atlassian.net/browse/PUBDEV-1553): Threshold in GLM is hardcoded to 0
- [GitHub](https://github.com/h2oai/h2o-3/commit/dc379b117cc5f26c38ae276aba82b6bb3d0fef2b): Make min_rows a double instead of int: Is now weighted number of observations (min_obs in R). 
- [GitHub](https://github.com/h2oai/h2o-3/commit/7cf9ba765c0fe8f1394439db69bc2aa54e004b75): Don't use sample weighted variance, but full weighted variance.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bf9838e84f527b52756de45a752bd321a62ba6e4): Fix R^2 computation.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b9cccbe02017a01167afed5ca1a64198d499fa0b): Skip rows with missing response in weighted mean computation.
- `_binomial_double_trees` disabled by default for DRF (was enabled). 
- [GitHub](https://github.com/h2oai/h2o-3/commit/25d6735b0b621b0ce67c67d96b5113e03eb045f1): Relax tolerance.
- [HEXDEV-329](https://0xdata.atlassian.net/browse/HEXDEV-329) : Offset for GBM
- [HEXDEV-211](https://0xdata.atlassian.net/browse/HEXDEV-211) : Tweedie distributions for GLM


#####API

- [PUBDEV-1491](https://0xdata.atlassian.net/browse/PUBDEV-1491): generated REST API POJOS should be compiled and jar'd up as part of the build
- [GitHub](https://github.com/h2oai/h2o-3/commit/1c5df7bb74238433699b23d7b8be6bcd0ba9f4e7): Change schema for PCA, SVD, and GLRM to version 99

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/7295701c1b1fa45817aab2fba39d209f37185d6b): is factor returns TRUE/FALSE cast to scalar 1/0
- [GitHub](https://github.com/h2oai/h2o-3/commit/4f932f4775ce7844114e84e9cfd8086c06cffb96): take a slightly different syntactic approach to dropping column
- [GitHub](https://github.com/h2oai/h2o-3/commit/c001961bf39a75e9d44b3b98a5567ca13aa09b85): better list comp in interaction call
- [GitHub](https://github.com/h2oai/h2o-3/commit/82b8f9bc3a13bb5ac3eb5885ef3637dac05262ea): if `weights_column` argument is specified, attach the column to the training and/or validation frame (if not already specified as part of x/validation_x). if weights_column is not already part of x/validation_x, then a training_frame/validation_frame needs to be provided and the weights column is taken from here. respective pyunit added

#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/62937f80722011a07dc07d6c32c95fbe3c64ba7c): better ref handling in the [<- for python and R
- [GitHub](https://github.com/h2oai/h2o-3/commit/231632b832c85305b92098a24ec87cba7af013fc): Pass binomial_double_trees in the R wrapper for DRF.
- [GitHub](https://github.com/h2oai/h2o-3/commit/bc2b9c2f073822dba4442a61e2fcf26bf2257b66): carefully format NAs and non NAs
- [GitHub](https://github.com/h2oai/h2o-3/commit/ca70709db3576f5ee641d6e1ddc3c4877212d400): for loop over the x[[j]] to format NAs properly
- [GitHub](https://github.com/h2oai/h2o-3/commit/818fb9a8df0c210acde4051948f83475f20a628a): Added example to h2o-r/ensemble/create_h2o_wrappers.R

#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/b3b7dab9fe7cf7ef7dab0a7dc08985c028183f4a): allow for no y in model_builder
- [GitHub](https://github.com/h2oai/h2o-3/commit/c1b302914157c55ad7ef778ec49e07e01b03e79d): Enable auto-flag for Java6 generation.
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac1a079e968e24b7f12471406b74ebb5c3785ac0): better compression in split frame
- [PUBDEV-1594](https://0xdata.atlassian.net/browse/PUBDEV-1594): All basic file accessors in PersistHDFS should check file permissions
- [PUBDEV-1518](https://0xdata.atlassian.net/browse/PUBDEV-1518): getFrames should show a Parse button for raw frames


#####Web UI

- [PUBDEV-1545](https://0xdata.atlassian.net/browse/PUBDEV-1545): Flow => Build model => ignored columns table => should have column width resizing based on column names width => looks odd if column names are short
- [PUBDEV-1546](https://0xdata.atlassian.net/browse/PUBDEV-1546): Flow: Build model => Search for 1 column => select it  => build model shows list of columns instead of 1 column
- [PUBDEV-1254](https://0xdata.atlassian.net/browse/PUBDEV-1254): Flow: Add Impute

####Bug Fixes 


#####Algorithms

- [PUBDEV-1554](https://0xdata.atlassian.net/browse/PUBDEV-1554): dl with offset: when offset same as response, do not get 0 mse
- [PUBDEV-1555](https://0xdata.atlassian.net/browse/PUBDEV-1555): h2oR: dl with offset giving : Error in args$x_ignore : object of type 'closure' is not subsettable
- [PUBDEV-1487](https://0xdata.atlassian.net/browse/PUBDEV-1487): gbm weights: give different terminal node predictions than R for attached data
- [PUBDEV-1569](https://0xdata.atlassian.net/browse/PUBDEV-1569): Investigate effectiveness of _binomial_double_trees (DRF) [GitHub](https://github.com/h2oai/h2o-3/commit/88dc897d69ce3e8f83ebbb7bd1d68cee2a0437a0)
- [PUBDEV-1574](https://0xdata.atlassian.net/browse/PUBDEV-1574): Actually pass 'binomial_double_trees' argument given to R wrapper to DRF.
- [PUBDEV-1444](https://0xdata.atlassian.net/browse/PUBDEV-1444): DL: h2o.saveModel cannot save metrics when a deeplearning model has a validation_frame
- [PUBDEV-1579](https://0xdata.atlassian.net/browse/PUBDEV-1579): GBM test time predictions without weights seem off when training with weights [GitHub](https://github.com/h2oai/h2o-3/commit/e4e260fa1ac5a856152bb3ceadcfffe53ee7c138)
- [PUBDEV-1533](https://0xdata.atlassian.net/browse/PUBDEV-1533): GLM: doubled weights should produce the same result as doubling the observations [GitHub](https://github.com/h2oai/h2o-3/commit/e302509a1db68d2695026a201e5128a66bb066f3)
- [PUBDEV-1531](https://0xdata.atlassian.net/browse/PUBDEV-1531): GLM: it appears that observations with 0 weights are not ignored, as they should be.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b4f82be57c4f8b6e00be095a08eb6fd34f40dbed): Fix a bug in PCA scoring that was handling categorical NAs inconsistently
- [PUBDEV-1581](https://0xdata.atlassian.net/browse/PUBDEV-1581): Regression 3060 fails on GLRM in R tests
- [PUBDEV-1586](https://0xdata.atlassian.net/browse/PUBDEV-1586): change Grid endpoints and schemas to v99 since they are still in flux
- [PUBDEV-1589](https://0xdata.atlassian.net/browse/PUBDEV-1589): GLM : build model => airlinesbillion dataset => IRLSM/LBFGS => fails with array index out of bound exception
- [PUBDEV-1607](https://0xdata.atlassian.net/browse/PUBDEV-1607): gbm w offset: predict seems to be wrong 
- [PUBDEV-1600](https://0xdata.atlassian.net/browse/PUBDEV-1600): Frame name creation fails when file name contains csv or zip (not as extension)
- [PUBDEV-1577](https://0xdata.atlassian.net/browse/PUBDEV-1577): DL predictions on test set require weights if trained with weights
- [PUBDEV-1598](https://0xdata.atlassian.net/browse/PUBDEV-1598): Flow: After running pca when call get Model/ jobs get: Failed to find schema for version: 3 and type: PCA
- [PUBDEV-1576](https://0xdata.atlassian.net/browse/PUBDEV-1576): Test variable importances for weights for GBM/DRF/DL
- [PUBDEV-1517](https://0xdata.atlassian.net/browse/PUBDEV-1517): With R, deep learning autoencoder using all columns in frame, not just those specified in x parameter
- [PUBDEV-1593](https://0xdata.atlassian.net/browse/PUBDEV-1593): dl var importance:there is a .missing(NA) variable in Dl variable importnce even when data has no nas


#####Python

- [PUBDEV-1538](https://0xdata.atlassian.net/browse/PUBDEV-1538): h2o.save_model fails on windoz due to path nonsense 
- [GitHub](https://github.com/h2oai/h2o-3/commit/27d3e1f1258a3ac1224b1a2dc5b58fa340d9d301): python leaked key check for Vecs, Chunks, and Frames
- [PUBDEV-1609](https://0xdata.atlassian.net/browse/PUBDEV-1609): frame dimension mismatch between upload/import method

#####R

- [PUBDEV-1601](https://0xdata.atlassian.net/browse/PUBDEV-1601): h2o.loadModel() from hdfs
- [PUBDEV-1611](https://0xdata.atlassian.net/browse/PUBDEV-1611): R CMD Check failing on : The Date field is over a month old.

#####System

- [PUBDEV-1514](https://0xdata.atlassian.net/browse/PUBDEV-1514): Large number of columns (~30000) on importFile (flow) is slow / unresponsive for long time
- [PUBDEV-841](https://0xdata.atlassian.net/browse/PUBDEV-841): Split frame : Flow should not show raw frames for SplitFrame dialog (water.exceptions.H2OIllegalArgumentException)
- [PUBDEV-1459](https://0xdata.atlassian.net/browse/PUBDEV-1459): bug in GLM POJO: seems threshold for binary predictions is always 0
- [PUBDEV-1566](https://0xdata.atlassian.net/browse/PUBDEV-1566): Cannot save model on windows since Key contains '@' (illegal character to path)
- [GitHub](https://github.com/h2oai/h2o-3/commit/7ad8406f895172da23b2e79a94a295ebc0fbea87): Fixes the timezone lists. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/923db4ff6ddd6efb49ac7ce07a5d4226e9ceb4b7): R CMD check fix for date
- [GitHub](https://github.com/h2oai/h2o-3/commit/30b4e51c1f13d6a7b89e67c81ef08b138a2b08cd): add ec2 back into project

#####Web UI

- [HEXDEV-54](https://0xdata.atlassian.net/browse/HEXDEV-54): Flow : Import file 100k.svm => Something went wrong while displaying page


---

###Shannon (3.0.0.25) - 6/25/15


####Enhancements

#####API 

- [PUBDEV-1452](https://0xdata.atlassian.net/browse/PUBDEV-1452): branch 3.0.0.2 to REGRESSION_REST_API_3 and cherry-pick the /99/Rapids changes to it

#####Web UI

- [PUBDEV-1545](https://0xdata.atlassian.net/browse/PUBDEV-1545): Flow => Build model => ignored columns table => should have column width resizing based on column names width => looks odd if column names are short
- [PUBDEV-1546](https://0xdata.atlassian.net/browse/PUBDEV-1546): Flow : Build model => Search for 1 column => select it  => build model shows list of columns instead of 1 column

####Bug Fixes 

The following changes are to resolve incorrect software behavior:

#####Algorithms

- [PUBDEV-1487](https://0xdata.atlassian.net/browse/PUBDEV-1487): gbm weights: give different terminal node predictions than R for attached data
- [GitHub](https://github.com/h2oai/h2o-3/commit/f17dc5e033ffb0ebd7e8fe16f37bca24aec197a4): Fix offset for DL.
- [GitHub](https://github.com/h2oai/h2o-3/commit/f1547e6a0497519646358bc39c73cf25c7935919): Gracefully handle 0 weight for GBM.

#####Python

- [PUBDEV-1547](https://0xdata.atlassian.net/browse/PUBDEV-1547): Weights API: weights column not found in python client 


#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b9bf679f27baec53cd5e5a46202b4e58cc0108f8): Fix R wrapper for DL for weights/offset.

#####Web UI

- [PUBDEV-1528](https://0xdata.atlassian.net/browse/PUBDEV-1528): Flow model builder: the na filter does not select all ignored columns; just the first 100.


---

###Shannon (3.0.0.24) - 6/25/15

####New Features

#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/cd7011b4810f11316a06fe33df0fd7d540268bce): Allow validation for unsupervised models.


#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/2a22657e5788be6b7b85362923c3de02ae4c0b16): Added runit GBM weights
- [GitHub](https://github.com/h2oai/h2o-3/commit/8f0b9dc95a155fbdf9a373a9181c80a3eb3e1ed6): Updated runit_GBM_weights.R

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/0606097d95a7accce3460a73daec463ad7ea4165): add h2o.set_timezone h2o.get_timezone and h2o.list_timezones to python client and respective pyunit. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/1eabf6db7cb45166ea6fbd01997b52eb41c6079d): add h2o.save_model and h2o.load_model to python client and respective pyunit


####Enhancements


#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/8b646239e9433c719d390b03ba475715cf3b4f5e): Skip rows with weight 0.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c6f11a9069d3553694dee3e33f574f482567613d): x_ignore must be set when autoencoder is TRUE


#####System

- [GitHub](https://github.com/h2oai/h2o-3/commit/c11201060001be2da98fb101dbdd8ffbe18e85bf): Fix Java bindings generator to generate code under project's location.
- [GitHub](https://github.com/h2oai/h2o-3/commit/8768ba503b79d7f485c7c757f056dc170c9aca45): Adds input parameter check to ParseSetup.

####Bug Fixes 



#####Algorithms

- [PUBDEV-1529](https://0xdata.atlassian.net/browse/PUBDEV-1529): dl with ae: get ava.lang.UnsupportedOperationException: Trying to predict with an unstable model.
- [GitHub](https://github.com/h2oai/h2o-3/commit/b5869fce2ff51c5afbad397324e220942f0490c3): Bring back accidentally removed hiding of classification-related fields for unsupervised models.

#####API

- [PUBDEV-1456](https://0xdata.atlassian.net/browse/PUBDEV-1456): fix REST API POJO generation for enums, + java.util.map import

---

###Shannon (3.0.0.23) - 6/19/15

####New Features

#####Algorithms

- [HEXDEV-21](https://0xdata.atlassian.net/browse/HEXDEV-21): Offset for GLM
- [HEXDEV-208](https://0xdata.atlassian.net/browse/HEXDEV-208): Add observation weights to GLM (was HEXDEV-4)
- [PUBDEV-677](https://0xdata.atlassian.net/browse/PUBDEV-677): Add observation weights to all metrics
- [PUBDEV-675](https://0xdata.atlassian.net/browse/PUBDEV-675): Pass a weight Vec as input to all algos
- [HEXDEV-6](https://0xdata.atlassian.net/browse/HEXDEV-6): Add observation weights to GBM
- [HEXDEV-7](https://0xdata.atlassian.net/browse/HEXDEV-7): Add observation weights to DL
- [HEXDEV-10](https://0xdata.atlassian.net/browse/HEXDEV-10): Add observation weights to DRF
- [PUBDEV-291](https://0xdata.atlassian.net/browse/PUBDEV-291): Add observation weights to GLM, GBM, DRF, DL (classification)
- [HEXDEV-332](https://0xdata.atlassian.net/browse/HEXDEV-323): Support Offsets for DL [GitHub](https://github.com/h2oai/h2o-3/commit/c6d6dc953c477aeaa5fed3c40af1b5583f590386)
- [GitHub](https://github.com/h2oai/h2o-3/commit/e72ba587c0aace574b0f600f0e3c72c2f551df80): Use weights/offsets in GBM.


#####API

- [PUBDEV-61](https://0xdata.atlassian.net/browse/PUBDEV-61): do back-end work to allow document navigation from one Schema to another
- [PUBDEV-133](https://0xdata.atlassian.net/browse/PUBDEV-133): doing summary means calling it with each columns name, index not supported?


#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/220c71470e40de92e7c5a94833ce71bb8addcd00): add num_iterations accessor to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/4206cda35543bef6ff8c930a3a66a5bfe01d30ba): add score_history accessor to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/709afea1e9d3edef1ebaead58d33aeb6bdc08da3): add hit ratio table accessor to python interface and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/04b4d82345d10578070ca3cbd558dab82b09807b): add h2o.naivebayes and respective pyunits
- [GitHub](https://github.com/h2oai/h2o-3/commit/46571731b0ad92829b8775a62e93bb7a51307b4e): add h2o.prcomp and respective pyunits.
- [PUBDEV-681](https://0xdata.atlassian.net/browse/PUBDEV-681): Add user-given input weight parameters to Python
- [GitHub](https://github.com/h2oai/h2o-3/commit/483fe5c8dc8d80a93c8ff2688221e3eb802d92cf): add h2o.create_frame to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/f1b0c315cdafa0bca76330e65cfca3462db29dc7): add h2o.interaction and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/fba655b02f6c33a0d5c4d74de25fa21b96d7c364): add h2o.strplit to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/09bec2687d32c717ea8e86331591acf1ba75b67a): add h2o.toupper and h2o.tolower to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/d370a2f4dc2aa73f6df7139d13c705b00d30ce1a): add h2o.sub and h2o.gsub to python interface and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/5496d10baa1ca1cb420f198b0a98e81cdbe000ec): add h2o.trim() to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/dfe0e8ed44e1c64824779d1cbc2322c159668a46): add h2o.rep_len to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/4cde35ac1dc3b96833206860c974e4ad9d099d27): add h2o.svd to python client and respective golden pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/e45ea385eea19f5c5964d96e5acae8c7c7b201b9): add scree plot functionality to python client and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/d583778e72cde01f73fbfaa30d10f8e3d3a6ab0e): add plotting functionality to python client and respective pyunit

#####R 

- [GitHub](https://github.com/h2oai/h2o-3/commit/edf3cfafc49307226425c38a4c5bef6fdd5ed7a9): added h2o.weights and h2o.biases accessors to R client and update respective runit
- [GitHub](https://github.com/h2oai/h2o-3/commit/06413c20912e36d41a9a158e28cbb697f4542d34): add h2o.centroid_stats to R client and respective runit 
- [PUBDEV-680](https://0xdata.atlassian.net/browse/PUBDEV-680): Add user-given input weight parameters to R
- [GitHub](https://github.com/h2oai/h2o-3/commit/3c5a80edcfe274294d43c837e7d6abb2216834e4): Add offset/weights to DRF/GBM R wrappers.


#####Web UI

- [PUBDEV-1513](https://0xdata.atlassian.net/browse/PUBDEV-1513): Add cancelJob() routine to Flow


####Enhancements

#####Algorithms

- [PUBDEV-676](https://0xdata.atlassian.net/browse/PUBDEV-676): Use the user-given weight Vec as observation weights for all algos
- [GitHub](https://github.com/h2oai/h2o-3/commit/42abac7758390f5f7b0b59fadddb0b07294d238e): Refactor the code to let the caller compute the weighted sigma.
- [GitHub](https://github.com/h2oai/h2o-3/commit/1025e08abff8200c4106b4a46aaf2175dfee6734): Modify prior class distribution to be computed from weighted response.
- [GitHub](https://github.com/h2oai/h2o-3/commit/eec4f863fc198adaf774314897bd8d3fb8df411e): Put back the defaultThreshold that's based on training/validation metrics. Was accidentally removed together with SupervisedModel.
- [GitHub](https://github.com/h2oai/h2o-3/commit/a9f5261991f96a511f1cf8d0863a9c9b1c14caf0): Always sample to at least #class labels when doing stratified sampling.
- [GitHub](https://github.com/h2oai/h2o-3/commit/4e30718943840d0bf8cde77d95d0211034ece15b): Cutout for NAs in GLM score0(data[],...), same as for score0(Chunk[],…)


#####R

- [PUBDEV-856](https://0xdata.atlassian.net/browse/PUBDEV-856): All h2o things in R should have an `h2o.something` version so it's unambiguous [GitHub](https://github.com/h2oai/h2o-3/commit/e488674502f3d02e853ecde93497c327f91ddad6)
- [GitHub](https://github.com/h2oai/h2o-3/commit/b99163db673b29eaa187d261a28365f80c0efdb9): export clusterIsUp and clusterInfo commands
- [GitHub](https://github.com/h2oai/h2o-3/commit/b514dd703904097feb1f0f6a8dc732948da5f4ec): update accessors in the shim
- [GitHub](https://github.com/h2oai/h2o-3/commit/62ef0590b1bb5b231ea98a3aea8a358bda9631b5): gbm with async exec


#####System

- [HEXDEV-361](https://0xdata.atlassian.net/browse/HEXDEV-361): Wide frame handling for model builders
- [GitHub](https://github.com/h2oai/h2o-3/commit/f408e1a3306c7b7768bbfeae1d3a90edfc583039): Remove application plugin from assembly to speedup build process.
- [GitHub](https://github.com/h2oai/h2o-3/commit/c3e91c670b69698d94dcf123511dc595b2d61927): add byteSize to ls
- [GitHub](https://github.com/h2oai/h2o-3/commit/ac10731a5695f699a45a8bfbd807a42432c82aec): option to launch randomForest async
- [GitHub](https://github.com/h2oai/h2o-3/commit/d986952c6328b8b831a20231bffa129067e3cc03): Return HDFS persist manager for URIs starting with s3n and s3a
- [GitHub](https://github.com/h2oai/h2o-3/commit/889a6573d6ff05616dc7a8d578e83444d64df184): quote strings when writing to disk


####Bug Fixes

#####Algorithms

- [PUBDEV-1217](https://0xdata.atlassian.net/browse/PUBDEV-1217): pca: when cancel the job the key remains locked
- [PUBDEV-1468](https://0xdata.atlassian.net/browse/PUBDEV-1468): Error in GBM if response column is constant [GitHub](https://github.com/h2oai/h2o-3/commit/5c9bfa7d72107baff323e255930e0b461498f744)
- [PUBDEV-1476](https://0xdata.atlassian.net/browse/PUBDEV-1476): dl with obs weights: nas in weights cause  'java.lang.AssertionError [GitHub](https://github.com/h2oai/h2o-3/commit/d296d7429d3a6b53ac12497b2c344ef6387c94e7)
- [PUBDEV-1458](https://0xdata.atlassian.net/browse/PUBDEV-1458): pca: data with nas, v2 vs v3 slightly different results [GitHub](https://github.com/h2oai/h2o-3/commit/c289d8731f99bf096975df2839f0223886dfef33)
- [PUBDEV-1477](https://0xdata.atlassian.net/browse/PUBDEV-1477): dl w/obs wts: when all wts are zero, get java.lang.AssertionError [GitHub](https://github.com/h2oai/h2o-3/commit/cf3e5e4fdf94bd278105b2bbca0d6e106913577e)
- [GitHub](https://github.com/h2oai/h2o-3/commit/959fe1d845db59086a9980c0baa97dbdccbe41c8): Fix check for offset (allow offset for logistic regression).
- [GitHub](https://github.com/h2oai/h2o-3/commit/10174b8a2199578b475a5588405db434026d4fe8): Gracefully handle exception when launching single-node DRF/GBM in client mode. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/a082d08f36d1e0b7cafd9492711feb0d5772f697): Hack around the fact that hasWeights()/hasOffset() isn't available on remote nodes and that SharedTree is sent to remote nodes and its private internal classes need access to the above methods...
- [GitHub](https://github.com/h2oai/h2o-3/commit/11b276c478944cac3a7010c6d9ee3871d83d1b71): Fix scoring when NAs are predicted.

#####Python

- [PUBDEV-1469](https://0xdata.atlassian.net/browse/PUBDEV-1469): pyunit_citi_bike_large.py : test failing consistently on regression jobs
- [PUBDEV-1472](https://0xdata.atlassian.net/browse/PUBDEV-1472): Regression job : Pyunit small tests groupie and pub_444_spaces failing consistently
- [PUBDEV-1372](https://0xdata.atlassian.net/browse/PUBDEV-1372): Regression of pyunit_small,  Groupby.py
- [PUBDEV-1386](https://0xdata.atlassian.net/browse/PUBDEV-1386): intermittent fail in pyunit_citi_bike_small.py: -Unimplemented- failed lookup on token
- [PUBDEV-1471](https://0xdata.atlassian.net/browse/PUBDEV-1471): pyunit_citi_bike_small.py : failing consistently on regression jobs
- [PUBDEV-1466](https://0xdata.atlassian.net/browse/PUBDEV-1466): matplotlib.pyplot import failure on MASTER jenkins pyunit small jobs [GitHub](https://github.com/h2oai/h2o-3/commit/b2edebe88984ff71782c6524c7d5e4cb18fb1f11)
- [GitHub](https://github.com/h2oai/h2o-3/commit/0bfbee20cf3fc9321ba9f7c22bb0914c7f830cd9): minor fix to python's h2o.create_frame
- [GitHub](https://github.com/h2oai/h2o-3/commit/e5b7ad8515999b90f841219357ebf03d2caccfce): update the path to jar in connection.py

#####R

- [PUBDEV-1475](https://0xdata.atlassian.net/browse/PUBDEV-1475): Client mode failed tests : runit_GBM_one_node.R, runit_RF_one_node.R, runit_v_3_apply.R, runit_v_4_createfunctions.R [GitHub](https://github.com/h2oai/h2o-3/commit/f270c3c99931f211303046c5bc2b36db004a170b)
- [PUBDEV-1235](https://0xdata.atlassian.net/browse/PUBDEV-1235): Split Frame causes AIOOBE on Chicago crimes data [GitHub](https://github.com/h2oai/h2o-3/commit/869926304eecb4be2e0d64c6d1fbf43e37a62cb6)
- [PUBDEV-746](https://0xdata.atlassian.net/browse/PUBDEV-746): runit_demo_NOPASS_h2o_impute_R : h2o.impute() is missing. seems like we want that?
- [PUBDEV-582](https://0xdata.atlassian.net/browse/PUBDEV-582): H2O-R-  does not give the full column summary
- [PUBDEV-1473](https://0xdata.atlassian.net/browse/PUBDEV-1473): Regression : Runit small jobs failing on tests : 
- [PUBDEV-741](https://0xdata.atlassian.net/browse/PUBDEV-741): runit_NOPASS_pub-668 R tests uses all() ...h2o says all is unimplemented
- [PUBDEV-1506](https://0xdata.atlassian.net/browse/PUBDEV-1506): R: h2o.ls() needs to return data sizes
- [PUBDEV-1436](https://0xdata.atlassian.net/browse/PUBDEV-1436): Intermitent runit fail : runit_GBM_ecology.R [GitHub](https://github.com/h2oai/h2o-3/commit/ccc11bc30a68bf82028b83d7e53e43d48cd67c50)
- [PUBDEV-1464](https://0xdata.atlassian.net/browse/PUBDEV-1464): R: toupper/tolower don't work [GitHub](https://github.com/h2oai/h2o-3/commit/26d7a37e50714a2ad025acbb561f2c7e52b1b9cb) [GitHub](https://github.com/h2oai/h2o-3/commit/8afa2ff12b0a3343dd902f3912f9f9d509e775ca)
- [PUBDEV-1194](https://0xdata.atlassian.net/browse/PUBDEV-1194): R: dataset is imported but can't return head of frame

#####Sparkling Water

- [PUBDEV-975](https://0xdata.atlassian.net/browse/PUBDEV-975): Download page for Sparkling Water should point to the right R-client and Python client
- [PUBDEV-1428](https://0xdata.atlassian.net/browse/PUBDEV-1428): Sparkling water => Flow => Million song/KDD Cup path issues [GitHub](https://github.com/h2oai/h2o-3/commit/9cd11646e5ea4b1e4ea4120ebe7ece8d049140a7)

## Web UI
- [PUBDEV-1433](https://0xdata.atlassian.net/browse/PUBDEV-1433): Flow UI: Change Help > FAQ link to h2o-docs/index.html#FAQ



---

###Shannon (3.0.0.22) - 6/13/15


####New Features

#####API

- [PUBDEV-633](https://0xdata.atlassian.net/browse/PUBDEV-633): Generate Java bindings for REST API: POJOs for the entities (schemas)

#####Python 

- [GitHub](https://github.com/h2oai/h2o-3/commit/9ab55a5e612af9d807f069863a50667dd6970484): added h2o.anyfactor() and respective pyunit
- [GitHub](https://github.com/h2oai/h2o-3/commit/6fa028bab1eb81800dabc0167b05bb7a4fc12731): add h2o.scale and respective pyunit 
- [GitHub](https://github.com/h2oai/h2o-3/commit/700fbfff9a835a964e6d62ea1c52853160e11902): added levels, nlevels, setLevel and setLevels and respective pyunit...PUBDEV-1434 PUBDEV-1437 PUBDEV-1434 PUBDEV-1345 PUBDEV-1311
- [GitHub](https://github.com/h2oai/h2o-3/commit/688da52517ab5582fbb6527c03950dbb365ce037): add H2OFrame.as_date and pyunit addition. H2OFrame.setLevel should return a H2OFrame not a H2OVec.

####Enhancements


#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/34f4110ac2d5a7fb6b47f1919851ade2e9f8f279): Add `_build_tree_one_node` option to GBM

##### API

- [HEXDEV-352](https://0xdata.atlassian.net/browse/HEXDEV-352): Additional attributes on /Frames and /Frames/foo/summary


#####R

- [PUBDEV-706](https://0xdata.atlassian.net/browse/PUBDEV-706): Release h2o-dev to CRAN
- Adding parameter `parse_type` to upload/import file [(GitHub)](https://github.com/h2oai/h2o-3/commit/7074685e0cea8ea956c98ebd02883045b52df63b)

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/9c697c0bc55195ae13365250b587efd49fd9cace): print out where h2o jar is looked for
- [GitHub](https://github.com/h2oai/h2o-3/commit/8e16c258324223a492ef9b39003082709b5715fa):add h2o.ls and respective pyunit


#####System

- [PUBDEV-717](https://0xdata.atlassian.net/browse/PUBDEV-717): refector the duplicated code in FramesV2
- [PUBDEV-1281](https://0xdata.atlassian.net/browse/PUBDEV-1281): Add horizontal pagination of frames to Flow [GitHub](https://github.com/h2oai/h2o-3/commit/5b9f0b84e79aa4dc09c7350c9b37c27d954b4c14)
- [PUBDEV-607](https://0xdata.atlassian.net/browse/PUBDEV-607): Add Xmx reporting to GA
- [GitHub](https://github.com/h2oai/h2o-3/commit/60a6e5d6705e04a2d8b7a6e45e13ae8a34013587):Added support for Freezable[][][] in serialization (added addAAA to auto buffer and DocGen, DocGen will just throw H2O.fail())
- [GitHub](https://github.com/h2oai/h2o-3/commit/75f6a6c87e943d2222597755788c8d9a23e8013f): No longer set yyyy-MM-dd and dd-MMM-yy dates that precede the epoch to be NA. Negative time values are fine. This unifies these two time formats with the behavior of as.Date.
- [GitHub](https://github.com/h2oai/h2o-3/commit/0bb1e10b5c888a7fd1274991348ea214302728db): Reduces the verbosity of parse tracing messages. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/04566d7a9efd15418885e46f3db9eba1d52b04d8): Rename AUTO->GUESS for figuring out file type.

##### Web UI

- [HEXDEV-276](https://0xdata.atlassian.net/browse/HEXDEV-276): Add frame pagination
- [PUBDEV-1405](https://0xdata.atlassian.net/browse/PUBDEV-1405): Flow : Decision to be made on display of number of columns for wider datasets for Parse and Frame summary
- [PUBDEV-1404](https://0xdata.atlassian.net/browse/PUBDEV-1404): Usability improvements
- [PUBDEV-244](https://0xdata.atlassian.net/browse/PUBDEV-244): "View Data" display may need to be modified/shortened.


####Bug Fixes


#####Algorithms 

- [PUBDEV-1365](https://0xdata.atlassian.net/browse/PUBDEV-1365): GLM: Buggy when likelihood equals infinity
- [PUBDEV-1394](https://0xdata.atlassian.net/browse/PUBDEV-1394): GLM: Some offsets hang
- [PUBDEV-1268](https://0xdata.atlassian.net/browse/PUBDEV-1268): GLM: get java.lang.AssertionError at hex.glm.GLM$GLMSingleLambdaTsk.compute2 for attached data
- [PUBDEV-1403](https://0xdata.atlassian.net/browse/PUBDEV-1403): pca: h2o-3 reporting incorrect proportion of variance and cum prop [GitHub](https://github.com/h2oai/h2o-3/commit/1c874f38b4927b3f9d2a30560dc697a78a2bfe13)
- [HEXDEV-281](https://0xdata.atlassian.net/browse/HEXDEV-281): GLM - beta constraints with categorical variables fails with AIOOB
- [HEXDEV-280](https://0xdata.atlassian.net/browse/HEXDEV-280): GLM - gradient not within tolerance when specifying beta_constraints w/ and w/o prior values



##### Python

- [PUBDEV-1425](https://0xdata.atlassian.net/browse/PUBDEV-1425): Class Cast Exception ValStr to ValNum [GitHub](https://github.com/h2oai/h2o-3/commit/7ed0befac7b47e867c017e4a52f9e4036e5f2aad)
- [PUBDEV-1421](https://0xdata.atlassian.net/browse/PUBDEV-1421): python client parse fail on hdfs /datasets/airlines/airlines.test.csv
- [PUBDEV-1153](https://0xdata.atlassian.net/browse/PUBDEV-1153): Demo: Airlines Demo in Python [GitHub](https://github.com/h2oai/h2o-3/commit/8f82d1de294c83f2fa9f2ab9e05ab0f829b8ec7f)
- [PUBDEV-1286](https://0xdata.atlassian.net/browse/PUBDEV-1286): Python ifelse on H2OFrame never finishes
- [PUBDEV-1435](https://0xdata.atlassian.net/browse/PUBDEV-1435): Run.py modify to accept phantomjs timeout command line option [GitHub](https://github.com/h2oai/h2o-3/commit/d720f4441e26bcd2715fb40b14370013a126d7c0)

##### R

- [PUBDEV-1154](https://0xdata.atlassian.net/browse/PUBDEV-1154): Demo: Chicago Crime Demo in R
- [PUBDEV-1240](https://0xdata.atlassian.net/browse/PUBDEV-1240): Merge causes IllegalArgumentException
- [PUBDEV-1447](https://0xdata.atlassian.net/browse/PUBDEV-1447): R: no argument parser_type in h2o.uploadFile/h2o.importFile [(GitHub)](https://github.com/h2oai/h2o-3/commit/b7a608d0031b25b23b869fdf9b0dd7ab4dc78fc6)


##### System

- [PUBDEV-1423](https://0xdata.atlassian.net/browse/PUBDEV-1423): Phantomjs : Add timeout command line option 
- [PUBDEV-1401](https://0xdata.atlassian.net/browse/PUBDEV-1401): Flow : Import file 15 M Rows 2.2K cols=> Parse these files => Change first column type => Unknown => Try to change other columns => Kind of hangs 
- [PUBDEV-1406](https://0xdata.atlassian.net/browse/PUBDEV-1406): make the ParseSetup / Parse API more efficient for high column counts [GitHub](https://github.com/h2oai/h2o-3/commit/4cc459401afbd7598d5c9a79a4b858237d91fc4f)


---

###Shannon (3.0.0.21) - 6/12/15

####New Features

##### Python
- [HEXDEV-29](https://0xdata.atlassian.net/browse/HEXDEV-29): The ability to define features as categorical or continuous in the web UI and in the python API


####Enhancements


#####Algorithms

- [GitHub](https://github.com/h2oai/h2o-3/commit/d39f0c885a02c9dafcabc78d4a108f7c8465eb32) Made intercept option public and added it to field list in parameter schema
- [GitHub](https://github.com/h2oai/h2o-3/commit/3ee8264d549f5cc62fe29e1b50e7e3d67ac6dde2) GLM: Updated null model intercept fit.
- [GitHub](https://github.com/h2oai/h2o-3/commit/74e7f6de084c69bdb54093d19fdb089401359ca2) GLM: Updated null-model constant term fitting when running with offset
- [GitHub](https://github.com/h2oai/h2o-3/commit/75903da9d1d1da3f34eb64024c1ff7d6955536de)  glm update
- [GitHub](https://github.com/h2oai/h2o-3/commit/3a6fab716dbc9a98106076c7a02d91291c1da88c) DL code refactoring to reduce file sizes

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/865e78d3b129df7dd776aca9d0a8d8824aab8287) add h2o.round() and h2o.signif() and additional pyunit checks
- [GitHub](https://github.com/h2oai/h2o-3/commit/c70f5a7b8eac16a0d00e1959346cab6ea28991e1) add h2o.all() and respective pyunit checks

#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/b25b2b3ce97ba1f87146850a472dc7166e1ef694) added intercept option top R 


#####System

- [PUBDEV-607](https://0xdata.atlassian.net/browse/PUBDEV-607): Add Xmx reporting to GA [GitHub](https://github.com/h2oai/h2o-3/commit/ba6ce79f679efb49ac4e77a462cc1bb080f9c64b)

##### Web UI

- [GitHub](https://github.com/h2oai/h2o-3/commit/385e0c2a2d008c3ee347441b0ab840c3a819c8b2) Add horizontal pagination of /Frames to handle UI navigation of wide datasets more efficiently. 
- [GitHub](https://github.com/h2oai/h2o-3/commit/d9c9a202a2f254ab004787e27d3bfe716ee76198) Only show the top 7 metrics for the max metrics table
- [GitHub](https://github.com/h2oai/h2o-3/commit/157c15833bdb8096e9e6f52286a6e557c59b8561) Make the max metrics table entries be called `max f1` etc. 


####Bug Fixes

The following changes are to resolve incorrect software behavior: 


##### Algorithms
- [PUBDEV-1365](https://0xdata.atlassian.net/browse/PUBDEV-1365): GLM: Buggy when likelihood equals infinity [GitHub](https://github.com/h2oai/h2o-3/commit/d9512529da2e4feed0d2d8847626a0c85e385cbe)
- [PUBDEV-1394](https://0xdata.atlassian.net/browse/PUBDEV-1394): GLM: Some offsets hang
- [PUBDEV-1268](https://0xdata.atlassian.net/browse/PUBDEV-1268): GLM: get java.lang.AssertionError at hex.glm.GLM$GLMSingleLambdaTsk.compute2 for attached data
- [PUBDEV-1382](https://0xdata.atlassian.net/browse/PUBDEV-1382): pca: giving wrong std- dev for mentioned data
- [PUBDEV-1383](https://0xdata.atlassian.net/browse/PUBDEV-1383): pca: std dev numbers differ for v2 and v3 for attached data [GitHub](https://github.com/h2oai/h2o-3/commit/3b02b5b1dcc9c6401823f3f0ba00e1578e3b4826)
- [PUBDEV-1381](https://0xdata.atlassian.net/browse/PUBDEV-1381): GBM, RF: get an NPE when run with a validation set with no response [GitHub](https://github.com/h2oai/h2o-3/commit/fe4dd15b6931bc92065884a36969d7bd519e5ec0)
- [GitHub](https://github.com/h2oai/h2o-3/commit/900929e99eeda7c885a984fe8bcc790c0339dbbe) GLM fix - fixed fitting of null model constant term
- [GitHub](https://github.com/h2oai/h2o-3/commit/fdebe7ade04cb1e0bf1c488e268815c46cbf2052) Fix remote bug
- [GitHub](https://github.com/h2oai/h2o-3/commit/2e679013a5c2a39023240f38890690658411be08) Remove elastic averaging parameters from Flow.
- [PUBDEV-1398](https://0xdata.atlassian.net/browse/PUBDEV-1398): pca: predictions on the attached data from v2 and v3 differ
 

##### Python
- [PUBDEV-1286](https://0xdata.atlassian.net/browse/PUBDEV-1286): Python ifelse on H2OFrame never finishes [GitHub](https://github.com/h2oai/h2o-3/commit/e64909d307aaa80524ed38d25fc9152af9594879)

##### R
- [PUBDEV-761](https://0xdata.atlassian.net/browse/PUBDEV-761): Save model and restore model (from R)
- [PUBDEV-1236](https://0xdata.atlassian.net/browse/PUBDEV-1236): h2o-r/tests/testdir_misc/runit_mergecat.R failure (client mode only)

##### System
- [PUBDEV-1402](https://0xdata.atlassian.net/browse/PUBDEV-1402): move Rapids to /99 since it's going to be in flux for a while [GitHub](https://github.com/h2oai/h2o-3/commit/cc908d2bc16f270e190f889cb4e67a3884b2ac74)
- [GitHub](https://github.com/h2oai/h2o-3/commit/ea61945a2185e3201ec23aed4bebeaa86f2cc05a) Fixes an operator precedence issue, and replaces debug GA target with actual one.
- [GitHub](https://github.com/h2oai/h2o-3/commit/40d13b4bb8c2342ac4022e3017490e651b6c9a9b) Fix log download bug where all nodes were getting the same zip file. 



---

###Shannon (3.0.0.18) - 6/9/15

####New Features

#####System

- [PUBDEV-1163](https://0xdata.atlassian.net/browse/PUBDEV-1163): implement h2o1-style model save/restore in h2o-3 [GitHub](https://github.com/h2oai/h2o-3/commit/204695c288d5d8fd833274461c3ee6ef19a65711)

#####Python

- [GitHub](https://github.com/h2oai/h2o-3/commit/baa8ac01fc93dac36b519ffbada962665c1ba802): Added --h2ojar option


####Enhancements


##### Python
- [PUBDEV-277](https://0xdata.atlassian.net/browse/PUBDEV-277): Make python equivalent of as.h2o() work for numpy array and pandas arrays


####Bug Fixes

#####Algorithms

- [PUBDEV-1371](https://0xdata.atlassian.net/browse/PUBDEV-1371): pca: get java.lang.AssertionError at hex.svd.SVD$SVDDriver.compute2(SVD.java:198)
- [PUBDEV-1376](https://0xdata.atlassian.net/browse/PUBDEV-1376): pca: predictions from h2o-3 and h2o-2 differs for attached data
- [PUBDEV-1380](https://0xdata.atlassian.net/browse/PUBDEV-1380): DL: when try to access the training frame from the link in the dl model get: Object not found

##### R
- [PUBDEV-761](https://0xdata.atlassian.net/browse/PUBDEV-761): Save model and restore model (from R) [GitHub](https://github.com/h2oai/h2o-3/commit/391ba5ba296aeb4b50ebf5658d90ab87f4bb2d49)


---

###Shannon (3.0.0.17) - 6/8/15

####New Features


##### Algorithms

- [HEXDEV-209](https://0xdata.atlassian.net/browse/HEXDEV-209):Poisson distributions for GLM

##### Python

- [PUBDEV-1270](https://0xdata.atlassian.net/browse/PUBDEV-1270): Python Interface needs H2O Cut Function [GitHub](https://github.com/h2oai/h2o-3/commit/f67341a2fa1b59d8365c9cf1600b21c85343ce03)
- [PUBDEV-1242](https://0xdata.atlassian.net/browse/PUBDEV-1242): Need equivalent of as.Date feature in Python [GitHub](https://github.com/h2oai/h2o-3/commit/99430c1fb5921365d9a2242f4c4d02a751e9e024)
- [PUBDEV-1165](https://0xdata.atlassian.net/browse/PUBDEV-1165): H2O Python needs Modulus Operations
- [HEXDEV-29](https://0xdata.atlassian.net/browse/HEXDEV-29): The ability to define features as categorical or continuous in the web UI and in the python API
- [PUBDEV-1237](https://0xdata.atlassian.net/browse/PUBDEV-1237): environment variable to disable the strict version check in the R and Python bindings


##### Web UI

- [PUBDEV-1175](https://0xdata.atlassian.net/browse/PUBDEV-1175): Flow: Good interactive confusion matrix for binomial
- [PUBDEV-1176](https://0xdata.atlassian.net/browse/PUBDEV-1176): Flow: Good confusion matrix for multinomial

####Enhancements



#####Algorithms 

- [GitHub](https://github.com/h2oai/h2o-3/commit/c308d5e2bed30378ea9e0032831e73ad4bc09f7a): GLM weights fix: regularize by sum of weights rather than number of observations
- [GitHub](https://github.com/h2oai/h2o-3/commit/6f23ac2ed1d3b36652e4b7d8ecf80c68a0b2e37c): GLM fix: added line search (and limited number of iterations) to constant term model fitting with offset (could enter infinite loop)
- [GitHub](https://github.com/h2oai/h2o-3/commit/fb4a82dbf74c49f9c55d5653dd8e2e038f9a998b): No longer warn if `binomial_double_trees` option is enabled for `_nclass`!=2
- [GitHub](https://github.com/h2oai/h2o-3/commit/ed7ec99e3f1ebb4c92ac21ea4016369a9f5b85d6): Fix CM table to have integer entries unless there are real-valued entries 
- [GitHub](https://github.com/h2oai/h2o-3/commit/acbaa4806e70e0fb2868e7bedfba6eda21469424): Add extra assertion for `train_samples_per_iteration`
- [GitHub](https://github.com/h2oai/h2o-3/commit/1f3c110915b8c281df19cd21b0f09571fa30e618): Update model during runtime of algorithm.
- [GitHub](https://github.com/h2oai/h2o-3/commit/67c894f6af9064bdcfa9feb877b3f5abd1fc22db): Changes to glm forloop to add offsets and add NOPASS/NOFEATURE functionality back to run.py


#####R

- [GitHub](https://github.com/h2oai/h2o-3/commit/ab7cf2948c70b68de07e48d346d8e9c264263a16): month was off by one, runit test edited
- [GitHub](https://github.com/h2oai/h2o-3/commit/e450d67f2d4e7fefa79e63f92c1fd9212f463a8d): Comments to clarify the policy on dates in H2O.


#####System

- [HEXDEV-344](https://0xdata.atlassian.net/browse/HEXDEV-344): Logs should include JVM launch parameters


##### Web UI

- [PUBDEV-467](https://0xdata.atlassian.net/browse/PUBDEV-467): Show Frames for DL weights/biases in Flow
- [PUBDEV-1221](https://0xdata.atlassian.net/browse/PUBDEV-1221): add a "I like this" style button with LinkedIn or Github (beside the Flow Assist Me button)
- [PUBDEV-1245](https://0xdata.atlassian.net/browse/PUBDEV-1245): Flow: use new `_exclude_fields` query parameter to speed up REST API usage

####Bug Fixes


#####Algorithms

- [PUBDEV-1353](https://0xdata.atlassian.net/browse/PUBDEV-1353): GLM: model with weights different in R than in H2o for attached data 
- [PUBDEV-1358](https://0xdata.atlassian.net/browse/PUBDEV-1358): GLM: when run with -ive weights, would be good to tell the user that -ive weights not allowed instead of throwing exception
- [PUBDEV-1264](https://0xdata.atlassian.net/browse/PUBDEV-1264): GLM: reporting incorrect null deviance [GitHub](https://github.com/h2oai/h2o-3/commit/8117c82014db5a5da461f3051a84fdda0de56fcc)
- [PUBDEV-1362](https://0xdata.atlassian.net/browse/PUBDEV-1362): GLM: when run with weights and offset get wrong ans
- [PUBDEV-1263](https://0xdata.atlassian.net/browse/PUBDEV-1263): GLM: name ordering for the coefficients is incorrect [GitHub](https://github.com/h2oai/h2o-3/commit/368d649246a019a629f020bfd69f3e0bf7d8983c)
- [PUBDEV-1261](https://0xdata.atlassian.net/browse/PUBDEV-1261): pca: wrong std dev for data with nas rest numeric cols [GitHub](https://github.com/h2oai/h2o-3/commit/ac0b63e86934bfa363a539fcf5760d40ca10ed7a)
- [PUBDEV-1218](https://0xdata.atlassian.net/browse/PUBDEV-1218): pca: progress bar not showing progress just the initial and final progress status [GitHub](https://github.com/h2oai/h2o-3/commit/519f2326587efd7bcea7fb8459e2883bfd0915db)
- [PUBDEV-1204](https://0xdata.atlassian.net/browse/PUBDEV-1204): pca: from flow when try to invoke build model, displays-ERROR FETCHING INITIAL MODEL BUILDER STATE
- [PUBDEV-1212](https://0xdata.atlassian.net/browse/PUBDEV-1212): pca: with enum column reporting (some junk) wrong stdev/ rotation [GitHub](https://github.com/h2oai/h2o-3/commit/91b1a954c6f9959bf4aabc440bef4c917bf649ee)
- [PUBDEV-1228](https://0xdata.atlassian.net/browse/PUBDEV-1228): pca: no std dev getting reported for attached data
- [PUBDEV-1233](https://0xdata.atlassian.net/browse/PUBDEV-1233): pca: std dev for attached data differ when run on  h2o-3 and h2o-2
- [PUBDEV-1258](https://0xdata.atlassian.net/browse/PUBDEV-1258): h2o.glm with offset column: get Error in .h2o.startModelJob(conn, algo, params) :    Offset column 'logInsured' not found in the training frame.

##### R

- [PUBDEV-1234](https://0xdata.atlassian.net/browse/PUBDEV-1234): h2o.setTimezone throwing an error [GitHub](https://github.com/h2oai/h2o-3/commit/6dbecb2707b9483218d40a95d717729db72f587b)
- [PUBDEV-1229](https://0xdata.atlassian.net/browse/PUBDEV-1229): R: Most GLM accessors fail [GitHub](https://github.com/h2oai/h2o-3/commit/790c5f4850362712e39b83c4489f315fc5ca89b8)
- [PUBDEV-1227](https://0xdata.atlassian.net/browse/PUBDEV-1227): R: Cannot extract an enum value using data[row,col] [GitHub](https://github.com/h2oai/h2o-3/commit/7704a16e510458c3e4b7cf539fb35bd93163dcde)
- [HEXDEV-339](https://0xdata.atlassian.net/browse/HEXDEV-339): Feature engineering: log (1+x) fails [GitHub](https://github.com/h2oai/h2o-3/commit/ab13a9229a7fb93eace068efe3f99eb248359fa7)
- [PUBDEV-1249](https://0xdata.atlassian.net/browse/PUBDEV-1249): h2o.glm: no way to specify offset or weights from h2o R [GitHub](https://github.com/h2oai/h2o-3/commit/9245e785614f9748344990df5000ebeec3bea398)
- [PUBDEV-1255](https://0xdata.atlassian.net/browse/PUBDEV-1255): create_frame: hangs with following msg in the terminal, java.lang.IllegalArgumentException: n must be positive  
- [PUBDEV-1361](https://0xdata.atlassian.net/browse/PUBDEV-1361): runit_hex_1841_asdate_datemanipulation.R fails intermittently [GitHub](https://github.com/h2oai/h2o-3/commit/b95e094c45630eca07d2f99e5a27ac9409603e28) 
- [PUBDEV-1361](https://0xdata.atlassian.net/browse/PUBDEV-1361): runit_hex_1841_asdate_datemanipulation.R fails intermittently


##### Sparkling Water

- [PUBDEV-692](https://0xdata.atlassian.net/browse/PUBDEV-692): Upgrade SparklingWater to Spark 1.3


#####System

- [PUBDEV-1288](https://0xdata.atlassian.net/browse/PUBDEV-1288): Confusion Matrix: class java.lang.ArrayIndexOutOfBoundsException', with msg '2' java.lang.ArrayIndexOutOfBoundsException: 2 at hex.ConfusionMatrix.createConfusionMatrixHeader [Github](https://github.com/h2oai/h2o-3/commit/63efca9d0a1a30074cc78bde90c564f9b8c766ff)
- [HEXDEV-323](https://0xdata.atlassian.net/browse/HEXDEV-323): SVMLight Parse Bug [GitHub](https://github.com/h2oai/h2o-3/commit/f60e97f4f913769de5a36b34a802057e7bb68db2)
- [PUBDEV-1207](https://0xdata.atlassian.net/browse/PUBDEV-1207): implement JSON field-filtering features: `_exclude_fields`
- [GitHub](https://github.com/h2oai/h2o-3/commit/c7892ce1a3bcb7f745f80f5f3fd5d7a14bc1f345): Fix a missing field update in Job. 
- [PUBDEV-65](https://0xdata.atlassian.net/browse/PUBDEV-65): Handling of strings columns in summary is broken
- [PUBDEV-1230](https://0xdata.atlassian.net/browse/PUBDEV-1230): Parse: get AIOOB when parses the attached file with first two cols as enum while h2o-2 does fine
- [PUBDEV-1377](https://0xdata.atlassian.net/browse/PUBDEV-1377): Get AIOOBE when parsing a file with fewer column names than columns [GitHub](https://github.com/h2oai/h2o-3/commit/17b975b11c91ffd33d42b8b087e691e5f4ba0416)
- [PUBDEV-1364](https://0xdata.atlassian.net/browse/PUBDEV-1364): Variable importance Object


#####Web UI

- [PUBDEV-1198](https://0xdata.atlassian.net/browse/PUBDEV-1198): Flow: Selecting "Cancel" for "Load Notebook" prompt clears current notebook anyway
- [PUBDEV-1172](https://0xdata.atlassian.net/browse/PUBDEV-1172): Model builder takes forever to load the column names in Flow, hence cannot build any models
- [PUBDEV-1248](https://0xdata.atlassian.net/browse/PUBDEV-1248): Flow GLM: from Flow the drop down with column names does not show up and hence not able to select the offset column
- [PUBDEV-1380](https://0xdata.atlassian.net/browse/PUBDEV-1380): DL: when try to access the training frame from the link in the dl model get: Object not found [GitHub](https://github.com/h2oai/h2o-3/commit/dee129ce73d24a54107f9634cc3fcfa18a783664)



---

###Shannon (3.0.0.13) - 5/30/15

####New Features


#####Algorithms

- [HEXDEV-260](https://0xdata.atlassian.net/browse/HEXDEV-260): Add Random Forests for regression [GitHub](https://github.com/h2oai/h2o-3/commit/66b1b67ba212445607615f2db65d96d87ac6029c)

##### Python

- [PUBDEV-1166](https://0xdata.atlassian.net/browse/PUBDEV-1166): Converting H2OFrame into Python object
- [PUBDEV-1165](https://0xdata.atlassian.net/browse/PUBDEV-1165): H2O Python needs Modulus Operations

#####R

- [PUBDEV-1188](https://0xdata.atlassian.net/browse/PUBDEV-1188): Merge should handle non-numeric columns [(github)](https://github.com/h2oai/h2o-3/commit/3ef148bd93c053c06eeb8414bc9290a394d082f8)
- [PUBDEV-1096](https://0xdata.atlassian.net/browse/PUBDEV-1096): R: add weekdays() function in addition to month() and year()


####Enhancements

#####Algorithms

- [github](https://github.com/h2oai/h2o-3/commit/09e5d53b6b1b3a1bfb45b6e5a12e1a05d877102f): Updated weights handling, test. 
- [HEXDEV-324](https://0xdata.atlassian.net/browse/HEXDEV-324)poor GBM performance on KDD Cup 2009 competition dataset [(github)](https://github.com/h2oai/h2o-3/commit/36b99ed538218d8f675266f97e2816b877c189bf)
- [HEXDEV-326](https://0xdata.atlassian.net/browse/HEXDEV-326): varImp() function for DRF and GBM [(github)](https://github.com/h2oai/h2o-3/commit/4bc6f08e8fbc55c2d5795fd30f0bc4d0481e2499)
- [github](https://github.com/h2oai/h2o-3/commit/201f8d11c1773cb3119c0294784bf47c08cf42ba): Change some of the defaults

#####API

- [PUBDEV-669](https://0xdata.atlassian.net/browse/PUBDEV-669): have the /Frames/{key}/summary API call Vec.startRollupStats

#####R/Python

- [PUBDEV-479](https://0xdata.atlassian.net/browse/PUBDEV-479): Port MissingInserter to R/Python
- [PUBDEV-632](https://0xdata.atlassian.net/browse/PUBDEV-632): Display TwoDimTable of HitRatios in R/Python
- [github](https://github.com/h2oai/h2o-3/commit/6ed0f24693ab872179336289207345517d6925de): minor change to h2o.demo()
- [github](https://github.com/h2oai/h2o-3/commit/a7fbe9f0734cfece37ab408eb644c853a344b964): add h2o.demo() facility to python package, along with some built-in (small) data
- [github](https://github.com/h2oai/h2o-3/commit/3475e47fd2271e317d167c07755561d19c6b8fc8): remove cols param


####Bug Fixes

#####Algorithms

- [PUBDEV-1211](https://0xdata.atlassian.net/browse/PUBDEV-1211): pca: descaled pca, std dev seems to be wrong for attached data [github](https://github.com/h2oai/h2o-3/commit/edfa4e30e72ecb02cc4c99c8d8a02313b58c0f63)
- [PUBDEV-1213](https://0xdata.atlassian.net/browse/PUBDEV-1213): pca: would be good to have the std dev numbered bec difficult to relate to the principle components [(github)](https://github.com/h2oai/h2o-3/commit/90ef7c083823fca05a0f94bf8c7e451ea64b646a)
- [PUBDEV-1201](https://0xdata.atlassian.net/browse/PUBDEV-1201): pca: get ArrayIndexOutOfBoundsException [(github)](https://github.com/h2oai/h2o-3/commit/1390a4bd4de3adcdb924658e9122f230f7521fea)
- [PUBDEV-1203](https://0xdata.atlassian.net/browse/PUBDEV-1203): pca: giving wrong std dev/rotation-labels for iris with species as enum [(github)](https://github.com/h2oai/h2o-3/commit/7ae347fcd26e96cdbd8b5fa3a42585cb5530fe01)
- [PUBDEV-1199](https://0xdata.atlassian.net/browse/PUBDEV-1199): DL with <1 epochs has wrong initial estimated time [(github)](https://github.com/h2oai/h2o-3/commit/5b6854954f037b645002accbf35f0670b01df41f)
- [github](https://github.com/h2oai/h2o-3/commit/5cbd138e06797954e6aa6996c5733a1eaf927316): Fix missing AUC for training data in DL. 
- [github](https://github.com/h2oai/h2o-3/commit/761b6ef5d75d7327d446e0b23e1fe74bc509b6a3): Add the seed back to GBM imbalanced test (was set to 0 by default before, now explicit) 


#####R

- [PUBDEV-1189](https://0xdata.atlassian.net/browse/PUBDEV-1189): R: h2o.hist broken for breaks that is a list of the break intervals [(github)](https://github.com/h2oai/h2o-3/commit/6118b04367cfc58c54f0c5ff51faf9b72a06088a)
- [PUBDEV-1206](https://0xdata.atlassian.net/browse/PUBDEV-1206): Frame summary from R and Python need to use the Frame summary endpoint [(github)](https://github.com/h2oai/h2o-3/commit/1ed38e5a4686e7ea4da56752d270e4d07450f402)
- [PUBDEV-1177](https://0xdata.atlassian.net/browse/PUBDEV-1177): R summary() is slow when large number of columns
- [PUBDEV-1097](https://0xdata.atlassian.net/browse/PUBDEV-1097): R: R should be able to take a of paths similar to how python does

---

###Shannon (3.0.0.11) - 5/22/15

####Enhancements

#####Algorithms

- [PUBDEV-1179](https://0xdata.atlassian.net/browse/PUBDEV-1179): DRF: investigate if larger seeds giving better models
- [PUBDEV-1178](https://0xdata.atlassian.net/browse/PUBDEV-1178): Add logloss/AUC/Error to GBM/DRF Logs & ScoringHistory
- [PUBDEV-1169](https://0xdata.atlassian.net/browse/PUBDEV-1169): Use only 1 tree for DRF binomial [(github)](https://github.com/h2oai/h2o-3/commit/84fce9cfab37a6c4fd73a22e9258685bc2fb124e)
- [PUBDEV-1170](https://0xdata.atlassian.net/browse/PUBDEV-1170): Wrong ROC is shown for DRF (Training ROC, even though Validation is given)
- [PUBDEV-1162](https://0xdata.atlassian.net/browse/PUBDEV-1162): Speed up sorting of histograms with O(N log N) instead of O(N^2)

#####System

- [PUBDEV-1152](https://0xdata.atlassian.net/browse/PUBDEV-1152): Accept s3a URLs
- [HEXDEV-316](https://0xdata.atlassian.net/browse/HEXDEV-316): ImportFiles should not download files from HTTP

####Bug Fixes

#####Algorithms

- [HEXDEV-253](https://0xdata.atlassian.net/browse/HEXDEV-253): model output consistency
- [HEXDEV-319](https://0xdata.atlassian.net/browse/HEXDEV-319): DRF in h2o 3.0 is worse than in h2o 2.0 for Airline
- [PUBDEV-1180](https://0xdata.atlassian.net/browse/PUBDEV-1180): DRF has wrong training metrics when validation is given



#####API

- [PUBDEV-501](https://0xdata.atlassian.net/browse/PUBDEV-501): H2OPredict: does not complain when you build a model with one dataset and predict on completely different dataset

#####Python

- [PUBDEV-1183](https://0xdata.atlassian.net/browse/PUBDEV-1183): Python version check should fail hard by default
- [PUBDEV-1185](https://0xdata.atlassian.net/browse/PUBDEV-1185): Python binding version mismatch check should fail hard and be on by default
- [HEXDEV-138](https://0xdata.atlassian.net/browse/HEXDEV-138): Port Python tests for Deep Learning


#####R

- [PUBDEV-1160](https://0xdata.atlassian.net/browse/PUBDEV-1160): R: h2o.hist doesn't support breaks argument
- [PUBDEV-1159](https://0xdata.atlassian.net/browse/PUBDEV-1159): R: h2o.hist takes too long to run
- [PUBDEV-1150](https://0xdata.atlassian.net/browse/PUBDEV-1150): R CMD Check: URLs not working
- [PUBDEV-1149](https://0xdata.atlassian.net/browse/PUBDEV-1149): R CMD check not happy with our use of .OnAttach
- [PUBDEV-1174](https://0xdata.atlassian.net/browse/PUBDEV-1174): R: h2o.hist FD implementation broken
- [PUBDEV-1167](https://0xdata.atlassian.net/browse/PUBDEV-1167): R: h2o.group_by broken
- [HEXDEV-318](https://0xdata.atlassian.net/browse/HEXDEV-318): the fix to H2O startup for the host unreachable from R causes a security hole
- [PUBDEV-1187](https://0xdata.atlassian.net/browse/PUBDEV-1187): FramesHandler.summary() needs to run summary on all Vecs concurrently.


#####System

- [PUBDEV-862](https://0xdata.atlassian.net/browse/PUBDEV-862): Building a model without training file -> NPE
- [HEXDEV-315](https://0xdata.atlassian.net/browse/HEXDEV-315): importFile fails: Error in fromJSON(txt, ...) : unexpected character: A
- [PUBDEV-1137](https://0xdata.atlassian.net/browse/PUBDEV-1137): Parse: upload and import gives different chunk compression on the same file
- [PUBDEV-1054](https://0xdata.atlassian.net/browse/PUBDEV-1054): Parse: h2o parses arff file incorrectly
- [PUBDEV-1181](https://0xdata.atlassian.net/browse/PUBDEV-1181): Rapids should queue and block on the back-end to prevent overlapping calls
- [PUBDEV-1184](https://0xdata.atlassian.net/browse/PUBDEV-1184): importFile fails for paths containing spaces



#####Web UI
- [PUBDEV-1182](https://0xdata.atlassian.net/browse/PUBDEV-1182): Flow: when upload file fails, the control does not come back to the flow screen, and have to refresh the whole page to get it back
- [PUBDEV-1131](https://0xdata.atlassian.net/browse/PUBDEV-1131): GBM crashes after calling getJobs in Flow

---

###Shannon (3.0.0.7) - 5/18/15

####Enhancements

##### API

- [PUBDEV-711](https://0xdata.atlassian.net/browse/PUBDEV-711): take a final look at all REST API parameter names and help strings
- [PUBDEV-757](https://0xdata.atlassian.net/browse/PUBDEV-757): Rename DocsV1 + DocsHandler to MetadataV1 + MetadataHandler
- [PUBDEV-1138](https://0xdata.atlassian.net/browse/PUBDEV-1138): Performance improvements for big data sets => getModels
- [PUBDEV-1126](https://0xdata.atlassian.net/browse/PUBDEV-1126): Performance improvements for big data sets => Get frame summary


#####System

- [HEXDEV-316](https://0xdata.atlassian.net/browse/HEXDEV-316): ImportFiles should not download files from HTTP


#####Web UI

- [PUBDEV-1144](https://0xdata.atlassian.net/browse/PUBDEV-1144): Update/Fix Flow API for CreateFrame


####Bug Fixes

The following changes are to resolve incorrect software behavior: 


##### API

- [PUBDEV-501](https://0xdata.atlassian.net/browse/PUBDEV-501): H2OPredict: does not complain when you build a model with one dataset and predict on completely different dataset
- [PUBDEV-1047](https://0xdata.atlassian.net/browse/PUBDEV-1047): API : Get frames and Build model => takes long time to get frames
- [HEXDEV-149](https://0xdata.atlassian.net/browse/HEXDEV-149): Allow JobsV3 to return properly typed jobs, not always instances of JobV3
- [PUBDEV-1036](https://0xdata.atlassian.net/browse/PUBDEV-1036): rename straggler V2 schemas to V3

##### R

- [PUBDEV-1159](https://0xdata.atlassian.net/browse/PUBDEV-1159): R: h2o.hist takes too long to run


#####System

- [PUBDEV-1034](https://0xdata.atlassian.net/browse/PUBDEV-1034): Windows 7/8/2012 Multicast Error UDP
- [PUBDEV-862](https://0xdata.atlassian.net/browse/PUBDEV-862): Building a model without training file -> NPE
- [HEXDEV-253](https://0xdata.atlassian.net/browse/HEXDEV-253): model output consistency
- [PUBDEV-1135](https://0xdata.atlassian.net/browse/PUBDEV-1135): While predicting get:class water.fvec.RollupStats$ComputeRollupsTask; class java.lang.ArrayIndexOutOfBoundsException: 5
- [PUBDEV-1090](https://0xdata.atlassian.net/browse/PUBDEV-1090): POJO: Models with "." in key name (ex. pros.glm) can't access pojo endpoint
- [PUBDEV-1077](https://0xdata.atlassian.net/browse/PUBDEV-1077): Getting an IcedHashMap warning from H2O startup

#####Web UI

- [PUBDEV-1133](https://0xdata.atlassian.net/browse/PUBDEV-1133): getModels in Flow returns error
- [PUBDEV-926](https://0xdata.atlassian.net/browse/PUBDEV-926): Flow: When user hits build model without specifying the training frame, it would be good if Flow  guides the user. It presently shows an NPE msg 
- [PUBDEV-1131](https://0xdata.atlassian.net/browse/PUBDEV-1131): GBM crashes after calling getJobs in Flow

---

###Shannon (3.0.0.2) - 5/15/15

####New Features 

##### ModelMetrics

- [PUBDEV-411](https://0xdata.atlassian.net/browse/PUBDEV-411): ModelMetrics by model category

##### WebUI

- [PUBDEV-942](https://0xdata.atlassian.net/browse/PUBDEV-942): ModelMetrics by model category - Autoencoder

####Enhancements

#####Algorithms

- [github](https://github.com/h2oai/h2o-dev/commit/6bdd386d4f1d6bde8d045691c8f250266f3142fc): GLM update: skip lambda max during lambda search
- [github](https://github.com/h2oai/h2o-dev/commit/e73b1e8316a100f60061ceb44eab4ff3c18f0452): removed higher accuracy option
- [github](https://github.com/h2oai/h2o-dev/commit/dd11a6fc8e279a8ecd65d412251a43421d2d32fb): Rename constant col parameter
- [github](https://github.com/h2oai/h2o-dev/commit/fa215e4247b8ed48e250d24914d65f46c0ecf5ad): GLM update: added stopping criteria to lbfgs, tweaked some internal constants in ADMM
- [github](https://github.com/h2oai/h2o-dev/commit/31bd600396195c250c9f1f1b8c67c86d41763cda): Add support for `ignore_const_col` in DL 


######Python

- [PUBDEV-852](https://0xdata.atlassian.net/browse/PUBDEV-852): Binomial: show per-metric-optimal CM and per-threshold CM in Python
- [github](https://github.com/h2oai/h2o-dev/commit/353d5438fc09fd5581c6b07f567f596e062fab08): add filterNACols to python 
- [github](https://github.com/h2oai/h2o-dev/commit/5a1971bb62805f1d862dca347e681e87b33a11da): h2o.delete replaced with h2o.removeFrameShallow
- [github](https://github.com/h2oai/h2o-dev/commit/98c8130036404735d42e2e8280a50626227a4f13): Add distribution summary to Python


#####R

- [github](https://github.com/h2oai/h2o-dev/commit/6e3d7938436bdf427e780269605896eb778aa74d): add filterNACols to R
- [github](https://github.com/h2oai/h2o-dev/commit/6b6c49605c6e673d4280542b719589589679d20e): explicitly set cols=TRUE for R style str on frames
- [github](https://github.com/h2oai/h2o-dev/commit/e342409fa536b6163873fda63118d9164ced46d3): enable faster str, bulk nlevels, bulk levels, bulk is.factor
- [github](https://github.com/h2oai/h2o-dev/commit/3d6e616fad8d1889670d2e270622425ab750961b): Add optional blocking parameter to h2o.uploadFile

##### System

- [PUBDEV-672](https://0xdata.atlassian.net/browse/PUBDEV-672) HTML version of the REST API docs should be available on the website
- [PUBDEV-827](https://0xdata.atlassian.net/browse/PUBDEV-827): class GenModel duplicates part of code of Model

#####Web UI

- [HEXDEV-181](https://0xdata.atlassian.net/browse/HEXDEV-181) Flow: Handle deep features prediction input and output
- [github](https://github.com/h2oai/h2o-dev/commit/7639e27): removed `use_all_factor_levels` from glm flows

####Bug Fixes

#####Algorithms

- [HEXDEV-302](https://0xdata.atlassian.net/browse/HEXDEV-302): AIOOBE during Prediction with DL [github](https://github.com/h2oai/h2o-dev/commit/e19d952b6b3cc787b542ba49e72868a2d8ab10de)
- [github](https://github.com/h2oai/h2o-dev/commit/b1df59e7d2396836ce3574acda0c69f7a49f9d54): glm fix: don't force in null model for lambda search with user given list of lambdas
- [github](https://github.com/h2oai/h2o-dev/commit/51608cbb392e28c018a56f74c670d5ab88d99947): Fix domain in glm scoring output for binomial
- [github](https://github.com/h2oai/h2o-dev/commit/5796b1f2ded1f984df0737f750e3e6d65e69cbd7): GLM Fix - fix degrees of freedom when running without intercept (+/-1)
- [github](https://github.com/h2oai/h2o-dev/commit/f8ee8a5f64266cf5803af80dadb48495c6b02e7b): GLM fix: make valid data info be clone of train data info (needs exactly the same categorical offsets, ignore unseen levels)
- [github](https://github.com/h2oai/h2o-dev/commit/a8659171c3d6a69a1723322beefcff52345ad512): Fix glm scoring, fill in default domain {0,1} for binary columns when scoring

#####R

- [PUBDEV-1116](https://0xdata.atlassian.net/browse/PUBDEV-1116): R: Parse that works from flow doesn't work from R using as.h2o
- [PUBDEV-798](https://0xdata.atlassian.net/browse/PUBDEV-798): R: String Munging Functions Missing
- [PUBDEV-584](https://0xdata.atlassian.net/browse/PUBDEV-584): R: hist() doesn't currently work for H2O objects
- [PUBDEV-820](https://0xdata.atlassian.net/browse/PUBDEV-820): H2oR: model objects should return the CM when run classification like h2o1 
- [PUBDEV-1113](https://0xdata.atlassian.net/browse/PUBDEV-1113): Remove Keys : Parse => Remove => doesn't complete
- [PUBDEV-1102](https://0xdata.atlassian.net/browse/PUBDEV-1102): R: h2o.rbind fails to join two dataset together 
- [PUBDEV-899](https://0xdata.atlassian.net/browse/PUBDEV-899): R: all doesn't work
- [PUBDEV-555](https://0xdata.atlassian.net/browse/PUBDEV-555): H2O-R: str does not work
- [PUBDEV-1110](https://0xdata.atlassian.net/browse/PUBDEV-1110): H2OR: while printing a gbm model object, get invalid format '%d'; use format %f, %e, %g or %a for numeric objects 
- [PUBDEV-903](https://0xdata.atlassian.net/browse/PUBDEV-903): R: Errors from some rapids calls seem to fail to return an error
- [HEXDEV-311](https://0xdata.atlassian.net/browse/HEXDEV-311): Performance bug from R with Expect: 100-continue
- [PUBDEV-1030](https://0xdata.atlassian.net/browse/PUBDEV-1030): h2o.performance: ignores the user specified threshold 
- [PUBDEV-1071](https://0xdata.atlassian.net/browse/PUBDEV-1071): R: regression models don't show in print statement r2 but it exists in the model object
- [PUBDEV-1072](https://0xdata.atlassian.net/browse/PUBDEV-1072): R: missing accessors for glm specific fields
- [PUBDEV-1032](https://0xdata.atlassian.net/browse/PUBDEV-1032): After running some R and  py demos when invoke a build model from flow get- rollup stats problem vec deleted error 
- [PUBDEV-1069](https://0xdata.atlassian.net/browse/PUBDEV-1069): R: missing implementation for h2o.r2
- [PUBDEV-1064](https://0xdata.atlassian.net/browse/PUBDEV-1064): Passing sep="," to h2o.importFile() fails with '400 Bad Request'
- [PUBDEV-1092](https://0xdata.atlassian.net/browse/PUBDEV-1092): Get NPE while predicting 


#####System

- [PUBDEV-1091](https://0xdata.atlassian.net/browse/PUBDEV-1091): S3 gzip parse failure
- [PUBDEV-1081](https://0xdata.atlassian.net/browse/PUBDEV-1081): Probably want to cleanly disable multicast (not retry) and print suggestion message, if multicast not supported on picked multicast network interface
- [PUBDEV-1112](https://0xdata.atlassian.net/browse/PUBDEV-1112): User has no way to specify whether to drop constant columns
- [PUBDEV-1109](https://0xdata.atlassian.net/browse/PUBDEV-1109): Change all extdata imports to uploadFile
- [PUBDEV-1104](https://0xdata.atlassian.net/browse/PUBDEV-1104): .gz file parse exception from local filesystem


##### Web UI

- [PUBDEV-1134](https://0xdata.atlassian.net/browse/PUBDEV-1134): getPredictions in Flow returns error
- [PUBDEV-1020](https://0xdata.atlassian.net/browse/PUBDEV-1020): Flow : Drop NA Cols enable => Should automatically populate the ignored columns 
- [PUBDEV-1041](https://0xdata.atlassian.net/browse/PUBDEV-1041): Flow GLM: formatting needed for the model parameter listing in the model object [github](https://github.com/h2oai/h2o-dev/commit/70babd4b275807913d21b77bd377e321636edee7)
- [PUBDEV-1108](https://0xdata.atlassian.net/browse/PUBDEV-1108): Flow: When predict on data with no response get :Error processing POST /3/Predictions/models/gbm-a179db76-ba96-420f-a643-0e166aea3af3/frames/subset_1  'undefined' is not an object (evaluating 'prediction.model')

---

##H2O-Dev

###Shackleford (0.2.3.6) - 5/8/15


####New Features 

#####Python

- Set up POJO download for Python client [(PUBDEV-908)](https://0xdata.atlassian.net/browse/PUBDEV-908) [(github)](https://github.com/h2oai/h2o-dev/commit/4b06cc2415f5d5b0bb0be6a6ef419ed6ff065ada)

#####Sparkling Water

- Publish h2o-scala and h2o-app latest version to maven central [(PUBDEV-443)](https://0xdata.atlassian.net/browse/PUBDEV-443)

####Enhancements

#####Algorithms

- Use AUC's default threshold for label-making for binomial classifiers predict() [(PUBDEV-1063)](https://0xdata.atlassian.net/browse/PUBDEV-1063) [(github)](https://github.com/h2oai/h2o-dev/commit/588a95df335d534080737832adf846e4c12ba7c6)
- GLM update [(github)](https://github.com/h2oai/h2o-dev/commit/c1c8e2e428554307870ac1a595bb35f60e258245)
- Cleanup AUC2, make incremental version [(github)](https://github.com/h2oai/h2o-dev/commit/2d7d064229f9577cafc9a6d08b47efc653e0c546)
- Name change: `override_with_best_model` -> `overwrite_with_best_model` [(github)](https://github.com/h2oai/h2o-dev/commit/f14dca82a529e2cb080800e258ca23dcb6ac9535)
- Couple of GLM updates [(github)](https://github.com/h2oai/h2o-dev/commit/05cec9710a3578789bb34f04a5134f4320ac7547)
- Disable `_replicate_training_data` for data that's larger than 10GB [(github)](https://github.com/h2oai/h2o-dev/commit/4a1fed5f292826a4bc89eafffc6c04bb7449644c)
- Added `replicate_training_data` param for DL [(github)](https://github.com/h2oai/h2o-dev/commit/e95e4870869d159f8d468e4193fc7201887f1661)
- Change a few kmeans output parameters so no longer dividing by `nrows` or `num_clusters` [(github)](https://github.com/h2oai/h2o-dev/commit/9933486a61113af5ef6d3ed329c70eb7fbdc61a8)
- GLMValidation Updated auc computation [(github)](https://github.com/h2oai/h2o-dev/commit/280e8f8390dfc5b4d6b5a571f06930bab9b5c7e5)
- Do not delete model metrics at end of GBM/DRF [(github)](https://github.com/h2oai/h2o-dev/commit/d10d4522eae38bfc3bf45208266b8b5e5806d524)


#####API 

- Clean REST api for Parse [(PUBDEV-993)](https://0xdata.atlassian.net/browse/PUBDEV-993)
- Removes `is_valid`, `invalid_lines`, and domains from REST api [(github)](https://github.com/h2oai/h2o-dev/commit/f5997de8f59f2eefd454afeb0e91a6a1d5c6672b)
- Annotate domains output field as expert level [(github)](https://github.com/h2oai/h2o-dev/commit/523af95008d3fb3b5d2269bb87a1de3235f6f828)

#####Python

- Implement h2o.interaction() [(PUBDEV-854)](https://0xdata.atlassian.net/browse/PUBDEV-854) [(github)](https://github.com/h2oai/h2o-dev/commit/3d43cb22afa0892c2c913b15e7b4bb5d4889443b)
- nice tables in ipython! [(github)](https://github.com/h2oai/h2o-dev/commit/fc6ecdc3d000375307f5731569a36a3c4e4fbf4c)
- added deeplearning weights and biases accessors and respective pyunit. [(github)](https://github.com/h2oai/h2o-dev/commit/7eb9f22262533ca7e335e9580af8afc3cf54c4b0)

#####R

- Cleaner client POJO download for R [(PUBDEV-907)](https://0xdata.atlassian.net/browse/PUBDEV-907)
- Implement h2o.interaction() [(PUBDEV-854)](https://0xdata.atlassian.net/browse/PUBDEV-854) [(github)](https://github.com/h2oai/h2o-dev/commit/58fa2f1e89bddd97b13a3884e15385ad0a5905d8)
- R: h2o.impute missing [(PUBDEV-796)](https://0xdata.atlassian.net/browse/PUBDEV-796)
- `validation_frame` is passed through to h2o [(github)](https://github.com/h2oai/h2o-dev/commit/184fe3a546e43c9b3d5664a808f6b30d3eaddab8)
- Adding GBM accessor function runits [(github)](https://github.com/h2oai/h2o-dev/commit/41d039196088df081ad77610d3e2d6550868f11b)
- Adding changes to `h2o.hit_ratio_table` to be like other accessors (i.e., no train) [(github)](https://github.com/h2oai/h2o-dev/commit/dc4a20151d9b415fe4708cff1bafc4fe61e802e0)
- add h2o.getPOJO to R, fix impute ast build in python [(github)](https://github.com/h2oai/h2o-dev/commit/8f192a7c87fa30782249af2e85ea2470fae491da)



#####System

- Change NA strings to an array in ParseSetup [(PUBDEV-995)](https://0xdata.atlassian.net/browse/PUBDEV-995)
- Document way of passing S3 credentials for S3N [(PUBDEV-947)](https://0xdata.atlassian.net/browse/PUBDEV-947)
- Add H2O-dev doc on docs.h2o.ai via a new structure (proposed below) [(PUBDEV-355)](https://0xdata.atlassian.net/browse/PUBDEV-355)
- Rapids Ref Doc [(PUBDEV-667)](https://0xdata.atlassian.net/browse/PUBDEV-667)
- Show Timestamp and Duration for all model scoring histories [(PUBDEV-1018)](https://0xdata.atlassian.net/browse/PUBDEV-1018) [(github)](https://github.com/h2oai/h2o-dev/commit/c02aa5efaf28ac21915c6fc427fc9b099aabee23)
- Logs slow reads, mainly meant for noting slow S3 reads [(github)](https://github.com/h2oai/h2o-dev/commit/d3b19e38ab083ea327ecea60a354cc91a22b68a8)
- Make prediction frame column names non-integer [(github)](https://github.com/h2oai/h2o-dev/commit/7fb855ca5eb546c03d1b7ea84b5b48093958ae9a)
- Add String[] factor_columns instead of int[] factors [(github)](https://github.com/h2oai/h2o-dev/commit/c381da2ae1a51b268b1f359d0594f3aea5feef04)
- change the runtime exception to a Log.info() if interface doesn't support multicast [(github)](https://github.com/h2oai/h2o-dev/commit/68f277c0ba8508bbebb34afac19f6233129bb55e)
- More robust way to copy Flow files to web root per Prithvi [(github)](https://github.com/h2oai/h2o-dev/commit/4e1b067e6456074107332c10b1af66443395325a)
- Switches `na_string` from a single value per column to an array per column [(github)](https://github.com/h2oai/h2o-dev/commit/a37ec777c10158a7afb29d1d5502f3c8082f6453)

#####Web UI

- Model output improvements [(HEXDEV-150)](https://0xdata.atlassian.net/browse/HEXDEV-150)


####Bug Fixes


#####Algorithms

- H2O cloud shuts down with some H2O.fail error, while building some kmeans clusters [(PUBDEV-1051)](https://0xdata.atlassian.net/browse/PUBDEV-1051) [(github)](https://github.com/h2oai/h2o-dev/commit/d95dec2a412e87e054fc000032da375023b87dce)
- GLM:beta constraint does not seem to be working [(PUBDEV-1083)](https://0xdata.atlassian.net/browse/PUBDEV-1083)
- GBM - random attack bug (probably because `max_after_balance_size` is really small) [(PUBDEV-1061)](https://0xdata.atlassian.net/browse/PUBDEV-1061) [(github)](https://github.com/h2oai/h2o-dev/commit/8625632c4759b07f75ac85acc43d69cdb9b38e15)
- GLM: LBFGS objval java lang assertion error [(PUBDEV-1042)](https://0xdata.atlassian.net/browse/PUBDEV-1042) [(github)](https://github.com/h2oai/h2o-dev/commit/dc4a20151d9b415fe4708cff1bafc4fe61e802e0)
- PCA Cholesky NPE [(PUBDEV-921)](https://0xdata.atlassian.net/browse/PUBDEV-921)
- GBM: H2o returns just 5525 trees, when ask for a much larger number of trees [(PUBDEV-860)](https://0xdata.atlassian.net/browse/PUBDEV-860)
- CM returned by AUC2 doesn't agree with manual-made labels from F1-optimal threshold [(HEXDEV-263)](https://0xdata.atlassian.net/browse/HEXDEV-263)
- AUC: h2o reporting wrong auc on a modified covtype data [(PUBDEV-891)](https://0xdata.atlassian.net/browse/PUBDEV-891)
- GLM: Build model => Predict => Residual deviance/Null deviance different from training/validation metrics [(PUBDEV-991)](https://0xdata.atlassian.net/browse/PUBDEV-991)
- KMeans metrics incomplete [(PUBDEV-1029)](https://0xdata.atlassian.net/browse/PUBDEV-1029)
- GLM: Java Assertion Error [(PUBDEV-1025)](https://0xdata.atlassian.net/browse/PUBDEV-1025)
- Random forest bug [(PUBDEV-1015)](https://0xdata.atlassian.net/browse/PUBDEV-1015)
- A particular random forest model has an empty (training) metric json `max_criteria_and_metric_scores` [(PUBDEV-1001)](https://0xdata.atlassian.net/browse/PUBDEV-1001)
- PCA results exhibit numerical inaccuracies compared to R [(PUBDEV-550)](https://0xdata.atlassian.net/browse/PUBDEV-550)
- DRF: reporting wrong depth for attached dataset [(PUBDEV-1006)](https://0xdata.atlassian.net/browse/PUBDEV-1006)
- added missing "names" column name to beta constraints processing [(github)](https://github.com/h2oai/h2o-dev/commit/fedcf159f8e842212812b0636b26ca9aa9ef1097)
- Fix `balance_classes` probability correction consistency between H2O and POJO [(github)](https://github.com/h2oai/h2o-dev/commit/5201f6da1196434866be6e70da996fb7c5967b7b)
- Fix in GLM scoring - check actual for NaNs as well [(github)](https://github.com/h2oai/h2o-dev/commit/e45c023a767dc26083f7fb26d9616ee234c03d2e)

#####Python

- Cannot import_file path=url python interface [(PUBDEV-1059)](https://0xdata.atlassian.net/browse/PUBDEV-1059)
- head()/tail() should show labels, rather than number encoding, for enum columns [(PUBDEV-1017)](https://0xdata.atlassian.net/browse/PUBDEV-1017)
- h2o.py: for binary response printing transpose and hence wrong cm [(PUBDEV-1013)](https://0xdata.atlassian.net/browse/PUBDEV-1013)

#####R

- Broken Summary in R [(PUBDEV-1073](https://0xdata.atlassian.net/browse/PUBDEV-1073)
- h2oR summary: displaying no labels in summary [(PUBDEV-1008)](https://0xdata.atlassian.net/browse/PUBDEV-1008)
- R/Python impute bugs [(PUBDEV-1055)](https://0xdata.atlassian.net/browse/PUBDEV-1055)
- R: h2o.varimp doubles the print statement [(PUBDEV-1068)](https://0xdata.atlassian.net/browse/PUBDEV-1068)
- R: h2o.varimp returns NULL when model has no variable importance [(PUBDEV-1078)](https://0xdata.atlassian.net/browse/PUBDEV-1078)
- h2oR: h2o.confusionMatrix(my_gbm, validation=F) should not show a null [(PUBDEV-849)](https://0xdata.atlassian.net/browse/PUBDEV-849)
- h2o.impute doesn't impute [(PUBDEV-1024)](https://0xdata.atlassian.net/browse/PUBDEV-1024)
- R: as.h2o cutting entries when trying to import data.frame into H2O [(HEXDEV-293)](https://0xdata.atlassian.net/browse/HEXDEV-293)
- The default names are too long, for an R-datafile parsed to H2O, and needs to be changed [(PUBDEV-976)](https://0xdata.atlassian.net/browse/PUBDEV-976)
- H2o.confusionMatrix: when invoked with threshold gives error [(PUBDEV-1010)](https://0xdata.atlassian.net/browse/PUBDEV-1010)
- removing train and adding error messages for valid = TRUE when there's not validation metrics [(github)](https://github.com/h2oai/h2o-dev/commit/cc3cf212300e252f987992e98d22a9fb6e46be3f)



#####System

- Download logs is returning the same log file bundle for every node [(PUBDEV-1056)](https://0xdata.atlassian.net/browse/PUBDEV-1056)
- ParseSetup is useless and misleading for SVMLight [(PUBDEV-994)](https://0xdata.atlassian.net/browse/PUBDEV-994)
- Fixes bug that was short circuiting the setting of column names [(github)](https://github.com/h2oai/h2o-dev/commit/5296456c425d9f9c0a467a2b65d448940f76c6a6)

#####Web UI

- Flow: Predict should not show mse confusion matrix etc [(PUBDEV-987)](https://0xdata.atlassian.net/browse/PUBDEV-987) [(github)](https://github.com/h2oai/h2o-dev/commit/6bc90e19cfefebd0db3ec4a46d3a157e258ff858)
- Flow: Raw frames left out after importing files from directory [(PUBDEV-1046)](https://0xdata.atlassian.net/browse/PUBDEV-1046)

---

###Shackleford (0.2.3.5) - 5/1/15

####New Features 

#####API

- Need a /Log REST API to log client-side errors to H2O's log [(HEXDEV-291)](https://0xdata.atlassian.net/browse/HEXDEV-291)


#####Python

- add impute to python interface [(github)](https://github.com/h2oai/h2o-dev/commit/8a4d39e8bca6a4acfb8fc5f01a8febe07e519a08)

#####System

- Job admission control [(PUBDEV-536)](https://0xdata.atlassian.net/browse/PUBDEV-536) [(github)](https://github.com/h2oai/h2o-dev/commit/f5ef7323c72cf4be2dabf57a298fcc3d6687e9dd)
- Get Flow Exceptions/Stack Traces in H2O Logs [(PUBDEV-920)](https://0xdata.atlassian.net/browse/PUBDEV-920)

####Enhancements

#####Algorithms

- GLM: Name to be changed from normalized to standardized in output to be consistent between input/output [(PUBDEV-954)](https://0xdata.atlassian.net/browse/PUBDEV-954)
- GLM: It would be really useful if the coefficient magnitudes are reported in descending order [(PUBDEV-923)](https://0xdata.atlassian.net/browse/PUBDEV-923)
- PUBDEV-536: Limit DL models to 100M parameters [(github)](https://github.com/h2oai/h2o-dev/commit/5678a26447704021d8905e7c37dfcd37b74b7327)
- PUBDEV-536: Add accurate memory-based admission control for GBM/DRF [(github)](https://github.com/h2oai/h2o-dev/commit/fc06a28c64d24ecb3a46a6a84d90809d2aae4875)
- relax the tolerance a little more...[(github)](https://github.com/h2oai/h2o-dev/commit/a24f4886b94b93f71452848af3a7d0f7b440779c)
- Tree depth correction [(github)](https://github.com/h2oai/h2o-dev/commit/2ad89a3eff0d8aa411b94b1d6f387051671b9bf8)
- Comment out `duration_in_ms` for now, as it's always left at 0 [(github)](https://github.com/h2oai/h2o-dev/commit/8008f017e10424623f966c141280d080f08f80b5)
- Updated min mem computation for glm [(github)](https://github.com/h2oai/h2o-dev/commit/446d5c30cdffcf04a4b7e0feaefa501187049efb)
- GLM update: added lambda search info to scoring history [(github)](https://github.com/h2oai/h2o-dev/commit/90ac3bb9cc07e4f50b50b08aad8a33279a0ff43d)

#####Python

- python .show() on model and metric objects should match R/Flow as much as possible [(HEXDEV-289)](https://0xdata.atlassian.net/browse/HEXDEV-289)
- GLM model output, details from Python [(HEXDEV-95)](https://0xdata.atlassian.net/browse/HEXDEV-95)
- GBM model output, details from Python [(HEXDEV-102)](https://0xdata.atlassian.net/browse/HEXDEV-102)
- Run GBM from Python [(HEXDEV-99)](https://0xdata.atlassian.net/browse/HEXDEV-99)
- map domain to result from /Frames if needed [(github)](https://github.com/h2oai/h2o-dev/commit/b1746a52cd4399d58385cd29914fa54870680093)
- added confusion matrix to metric output [(github)](https://github.com/h2oai/h2o-dev/commit/f913cc1643774e9c2ec5455620acf11cbd613711)
- update `metrics_base_confusion_matrices()` [(github)](https://github.com/h2oai/h2o-dev/commit/41c0a4b0079426860ac3b65079d6be0e46c6f69c)
- fetch out `string_data` if type is string [(github)](https://github.com/h2oai/h2o-dev/commit/995e135e0a49e492cccfb65974160b04c764eb11)

#####R

- GBM model output, details from R [(HEXDEV-101)](https://0xdata.atlassian.net/browse/HEXDEV-101)
- Run GBM from R [(HEXDEV-98)](https://0xdata.atlassian.net/browse/HEXDEV-98)
- check if it's a frame then check NA [(github)](https://github.com/h2oai/h2o-dev/commit/d61de7d0b8a9dac7d5d6c7f841e19c88983308a1)

#####System

- Report MTU to logs [(PUBDEV-614)](https://0xdata.atlassian.net/browse/PUBDEV-614) [(github)](https://github.com/h2oai/h2o-dev/commit/bbc3ad54373a2c865ce913917ef07c9892d62603)
- Make parameter changes Log.info() instead of Log.warn() [(github)](https://github.com/h2oai/h2o-dev/commit/7047a46fff612f41cc678f297cfcbc57ed8165fd)


#####Web UI

- Flow: Confusion matrix: good to have consistency in the column and row name (letter) case [(PUBDEV-971)](https://0xdata.atlassian.net/browse/PUBDEV-971)
- Run GBM Multinomial from Flow [(HEXDEV-111)](https://0xdata.atlassian.net/browse/HEXDEV-111)
- Run GBM Regression from Flow [(HEXDEV-112)](https://0xdata.atlassian.net/browse/HEXDEV-112)
- Sort model types in alphabetical order in Flow [(PUBDEV-1011)](https://0xdata.atlassian.net/browse/PUBDEV-1011)



####Bug Fixes

The following changes are to resolve incorrect software behavior: 

#####Algorithms

- GLM: Model output display issues [(PUBDEV-956)](https://0xdata.atlassian.net/browse/PUBDEV-956)
- h2o.glm: ignores validation set [(PUBDEV-958)](https://0xdata.atlassian.net/browse/PUBDEV-958)
- DRF: reports wrong number of leaves in a summary [(PUBDEV-930)](https://0xdata.atlassian.net/browse/PUBDEV-930)
- h2o.glm: summary of a prediction frame gives na's as labels [(PUBDEV-959)](https://0xdata.atlassian.net/browse/PUBDEV-959)
- GBM: reports wrong max depth for a binary model on german data [(PUBDEV-839)](https://0xdata.atlassian.net/browse/PUBDEV-839)
- GLM: Confusion matrix missing in R for binomial models [(PUBDEV-950)](https://0xdata.atlassian.net/browse/PUBDEV-950) [(github)](https://github.com/h2oai/h2o-dev/commit/d8845e3245491a85c2cc6c932d5fad2c260c19d3)
- GLM: On airlines(40g) get ArrayIndexOutOfBoundsException [(PUBDEV-967)](https://0xdata.atlassian.net/browse/PUBDEV-967)
- GLM: Build model => Predict => Residual deviance/Null deviance different from training/validation metrics [(PUBDEV-991)](https://0xdata.atlassian.net/browse/PUBDEV-991)
- Domains returned by GLM for binomial classification problem are integers, but should be mapped to their label [(PUBDEV-999)](https://0xdata.atlassian.net/browse/PUBDEV-999)
- GLM: Validation on non training data gives NaN Res Deviance and AIC [(PUBDEV-1005)](https://0xdata.atlassian.net/browse/PUBDEV-1005)
- Confusion matrix has nan's in it [(PUBDEV-1000)](https://0xdata.atlassian.net/browse/PUBDEV-1000)
- glm fix: pass `model_id` from R (was being dropped) [(github)](https://github.com/h2oai/h2o-dev/commit/9d8698177a9d0a70668d2d51005947d0adda0292)

#####Python

- H2OPy: warns about version mismatch even when installed the latest from master [(PUBDEV-980)](https://0xdata.atlassian.net/browse/PUBDEV-980)
- Columns of type enum lose string label in Python H2OFrame.show() [(PUBDEV-965)](https://0xdata.atlassian.net/browse/PUBDEV-965)
- Bug in H2OFrame.show() [(HEXDEV-295)](https://0xdata.atlassian.net/browse/HEXDEV-295) [(github)](https://github.com/h2oai/h2o-dev/commit/b319969cff0f0e7a805e49563e863a1dbb0e1aa0)


#####R

- h2o.confusionMatrix for binary response gives not-found thresholds [(PUBDEV-957)](https://0xdata.atlassian.net/browse/PUBDEV-957)
- GLM: model_id param is ignored in R [(PUBDEV-1007)](https://0xdata.atlassian.net/browse/PUBDEV-1007)
- h2o.confusionmatrix: mixing cases(letter) for categorical labels while printing multinomial cm [(PUBDEV-996)](https://0xdata.atlassian.net/browse/PUBDEV-996)
- fix the dupe thresholds error [(github)](https://github.com/h2oai/h2o-dev/commit/e40d4fd50cfd9438b2f693228ca20ad4d6648b46)
- extra arg in impute example [(github)](https://github.com/h2oai/h2o-dev/commit/5a41e7672fa30b2e66a1261df8976d18e89f0057)
- fix missing param data [(github)](https://github.com/h2oai/h2o-dev/commit/6719d94b30caf214fac2c61759905c7d5d57a9ac)


#####System

- Builds : Failing intermittently due to java.lang.StackOverflowError [(PUBDEV-972)](https://0xdata.atlassian.net/browse/PUBDEV-972)
- Get H2O cloud hang with NPE and roll up stats problem, when click on build model glm from flow, on laptop after running a few python demos and R scripts [(PUBDEV-963)](https://0xdata.atlassian.net/browse/PUBDEV-963)

#####Web UI

- Flow :=> Airlines dataset => Build models glm/gbm/dl => water.DException$DistributedException: from /172.16.2.183:54321; by class water.fvec.RollupStats$ComputeRollupsTask; class java.lang.NullPointerException: null [(PUBDEV-603)](https://0xdata.atlassian.net/browse/PUBDEV-603)
- Flow => Preview Pojo => collapse not working [(PUBDEV-977)](https://0xdata.atlassian.net/browse/PUBDEV-977)
- Flow => Any algorithm => Select response => Select Add all for ignored columns => Try to unselect some from ignored columns => Build => Response column IsDepDelayed not found in frame: allyears_1987_2013.hex. [(PUBDEV-978)](https://0xdata.atlassian.net/browse/PUBDEV-978)
- Flow => ROC curve select something on graph => Table is displayed for selection => Collapse ROC curve => Doesn't collapse table, collapses only graph [(PUBDEV-1003)](https://0xdata.atlassian.net/browse/PUBDEV-1003)



---

###Severi (0.2.2.16) - 4/29/15

####New Features 

#####Python

- Release h2o-dev to PyPi [(PUBDEV-762)](https://0xdata.atlassian.net/browse/PUBDEV-762)
- Python Documentation [(PUBDEV-901)](https://0xdata.atlassian.net/browse/PUBDEV-901)
- Python docs Wrap Up [(PUBDEV-966)](https://0xdata.atlassian.net/browse/PUBDEV-966)
- add getters for res/null dev, fix kmeans,dl getters [(github)](https://github.com/h2oai/h2o-dev/commit/3f9839c25628e44cba77b44905c38c21bee60a9c)



####Enhancements

#####Algorithms 

- Use partial-sum version of mat-vec for DL POJO [(PUBDEV-936)](https://0xdata.atlassian.net/browse/PUBDEV-936)
- Always store weights and biases for DLTest Junit [(github)](https://github.com/h2oai/h2o-dev/commit/5bcbad8e07fd592e2db701adf9b4974a5b4470b1)
- Show the DL model size in the model summary [(github)](https://github.com/h2oai/h2o-dev/commit/bdba19a99b863cd2f49ff1bdcd4ca648b60d1372)
- Remove assertion in hot loop [(github)](https://github.com/h2oai/h2o-dev/commit/9d1682e2821fc648dda02497ba5200e45bd6b6f5)
- Rename ADMM to IRLSM [(github)](https://github.com/h2oai/h2o-dev/commit/6a108d38e7b9473a792a5ba36b58a860166c84c4)
- Added no intercept option to glm [(github)](https://github.com/h2oai/h2o-dev/commit/6d99bd194cbc4500f519e306f28384d7dca407e1)
- Code cleanup. Moved ModelMetricsPCAV3 out of H2O-algos [(github)](https://github.com/h2oai/h2o-dev/commit/1f691681407b579ed0b71e4e6d452120dc3263dd)
- Improve DL model checkpoint logic [(github)](https://github.com/h2oai/h2o-dev/commit/9a13070c0de6ac2bf34b0e60c305de7358711965)
- Updated glm output [(github)](https://github.com/h2oai/h2o-dev/commit/4359a17f573bf27f0ac5e078143299de09011325)
- Renamed normalized coefficients to standardized coefficients in glm output [(github)](https://github.com/h2oai/h2o-dev/commit/39b814d37e9e161d1dd943741afcff59fd83d745)
- Use proper tie breaking for NB [(github)](https://github.com/h2oai/h2o-dev/commit/4bbbd1b6161e8d2d62f8d3d9cb600e3c6d678653)
- Add check that DL parameters aren't modified by model training [(github)](https://github.com/h2oai/h2o-dev/commit/84d4ab6bc63b314bab4f38e629e77fb8207f705f)
- Reduce tolerances [(github)](https://github.com/h2oai/h2o-dev/commit/0654d3c2d644abb9aa0d0c25e032db1a4fd219ad)
- If no observations of a response leveland prediction is numeric, assume it is drawn from standard normal distribution (mean 0, standard deviation 1). Add validation test with split frame for naive Bayes [(github)](https://github.com/h2oai/h2o-dev/commit/50a5d9cbb1f77db568a23573f6cff0cf45cb36af)



#####Python

- replaced H2OFrame.send_frame() calls with cbind Exprs so that lazy evaluation is enforced [(github)](https://github.com/h2oai/h2o-dev/commit/2799b8cb2d01270556d4481a40af4a8da6f0519f)
- change default xmx/s behavior of h2o.init() [(github)](https://github.com/h2oai/h2o-dev/commit/843a232c52e6b357dbd84db3253b3e33b8297803)
- better handling of single row return and print [(github)](https://github.com/h2oai/h2o-dev/commit/b2e782bf17352009992ad1252762f43977f95c8b)


#####R

- Added interpolation to quantile to match R type 7 [(github)](https://github.com/h2oai/h2o-dev/commit/a330ffb6ff30c5500e3fb6a80fe92ac8b123a4be)
- Removed and tidied if's in quantile.H2OFrame since it now uses match.arg [(github)](https://github.com/h2oai/h2o-dev/commit/237306039a3e2483c92ac310e157ec515b885530)
- Connected validation dataset to glm in R [(github)](https://github.com/h2oai/h2o-dev/commit/e71895bd3fc7507092f65cbde6a914f74dacf85d)
- Removing h2o.aic from seealso link (doesn't exist) and updating documentation [(github)](https://github.com/h2oai/h2o-dev/commit/8fa994efea831722dd333327789a858ed902bc79)


#####System

- Add number of rows (per node) to ChunkSummary [(PUBDEV-938)](https://0xdata.atlassian.net/browse/PUBDEV-938) [(github)](https://github.com/h2oai/h2o-dev/commit/06d33469e0fabb0ae452f29dc633647aef8c9bb3)
- allow nrow as alias for count in groupby [(github)](https://github.com/h2oai/h2o-dev/commit/fbeef36b9dfea422dfed7f209a196731d9312e8b)
- Only launches task to fill in SVM zeros if the file is SVM [(github)](https://github.com/h2oai/h2o-dev/commit/d816c52a34f2e8f549f8a3b0bf7d976333366553)
- Adds more log traces to track progress of post-ingest actions [(github)](https://github.com/h2oai/h2o-dev/commit/c0073164d8392fd2d079db840b84e6330bebe2e6)
- Adds svm as a file extension to the hex name cleanup [(github)](https://github.com/h2oai/h2o-dev/commit/0ad9eec48650491f5ec2e01c010be9987dac0a21)

#####Web UI

- Flow: Inspect data => Round decimal points to 1 to be consistent with h2o1 [(PUBDEV-453)](https://0xdata.atlassian.net/browse/PUBDEV-453)
- Setup POJO download method for Flow [(PUBDEV-909)](https://0xdata.atlassian.net/browse/PUBDEV-909)
- Pretty-print POJO preview in flow [(PUBDEV-940)](https://0xdata.atlassian.net/browse/PUBDEV-940)
- Flow: It would be good if 'get predictions' also shows the data [(PUBDEV-883)](https://0xdata.atlassian.net/browse/PUBDEV-883)
- GBM model output, details in Flow [(HEXDEV-103)](https://0xdata.atlassian.net/browse/HEXDEV-103)
- Display a linked data table for each visualization in Flow [(PUBDEV-318)](https://0xdata.atlassian.net/browse/PUBDEV-318)
- Run GBM binomial from Flow (needs proper CM) [(PUBDEV-943)](https://0xdata.atlassian.net/browse/PUBDEV-943)



####Bug Fixes


#####Algorithms

- GLM: results from model and prediction on the same dataset do not match [(PUBDEV-922)](https://0xdata.atlassian.net/browse/PUBDEV-922) 
- GLM: when select AUTO as solver, for prostate, glm gives all zero coefficients [(PUBDEV-916)](https://0xdata.atlassian.net/browse/PUBDEV-916)
- Large (DL) models cause oversize issues during serialization [(PUBDEV-941)](https://0xdata.atlassian.net/browse/PUBDEV-941)
- Fixed name change for ADMM [(github)](https://github.com/h2oai/h2o-dev/commit/bc126aa8d4d7c5901ef90120c7997c67466922ae)

#####API

- Fix schema warning on startup [(PUBDEV-946)](https://0xdata.atlassian.net/browse/PUBDEV-946) [(github)](https://github.com/h2oai/h2o-dev/commit/bd9ae8013bc0de261e7258af85784e9e6f20df5e)


#####Python

- H2OVec.row_select(H2OVec) fails on case where only 1 row is selected [(PUBDEV-948)](https://0xdata.atlassian.net/browse/PUBDEV-948)
- fix pyunit [(github)](https://github.com/h2oai/h2o-dev/commit/79344be836d9111fee77ddebe034234662d7064f)

#####R 

- R: Parse of zip file fails, Summary fails on citibike data [(PUBDEV-835)](https://0xdata.atlassian.net/browse/PUBDEV-835)
- h2o. performance reports a different Null Deviance than the model object for the same dataset [(PUBDEV-816)](https://0xdata.atlassian.net/browse/PUBDEV-816)
- h2o.glm: no example on h2o.glm help page [(PUBDEV-962)](https://0xdata.atlassian.net/browse/PUBDEV-962)
- H2O R: Confusion matrices from R still confused [(PUBDEV-904)](https://0xdata.atlassian.net/browse/PUBDEV-904) [(github)](https://github.com/h2oai/h2o-dev/commit/36c887ddadd47682745b64812e081dcb2fa36659)
- R: h2o.confusionMatrix("H2OModel", ...) extra parameters not working [(PUBDEV-953)](https://0xdata.atlassian.net/browse/PUBDEV-953) [(github)](https://github.com/h2oai/h2o-dev/commit/ca59b2be46dd07caad60882b5c1daed0ee4837c6)
- h2o.confusionMatrix for binomial gives not-found thresholds on S3 -airlines 43g [(PUBDEV-957)](https://0xdata.atlassian.net/browse/PUBDEV-957)
- H2O summary quartiles outside tolerance of (max-min)/1000 [(PUBDEV-671)](https://0xdata.atlassian.net/browse/PUBDEV-671)
- fix space headers issue from R (was not url-encoding the column strings) [(github)](https://github.com/h2oai/h2o-dev/commit/f121b0324e981e229cd2704df11a0a946d4b2aeb)
- R CMD fixes [(github)](https://github.com/h2oai/h2o-dev/commit/62a1d7df8bceeea181b87d83f922db854f28b6db)
- Fixed broken R interface - make `validation_frame` non-mandatory [(github)](https://github.com/h2oai/h2o-dev/commit/18fba95392f94e566b80797839e5eb2899057333)

#####Sparkling Water

- Sparkling water : #UDP-Recv ERRR: UDP Receiver error on port 54322java.lang.ArrayIndexOutOfBoundsException:[(PUBDEV-311)](https://0xdata.atlassian.net/browse/PUBDEV-311)


#####System

- Mapr 3.1.1 : Memory is not being allocated for what is asked for instead the default is what cluster gets [(PUBDEV-937)](https://0xdata.atlassian.net/browse/PUBDEV-937)
- GLM: AIOOBwith msg '-14' at water.RPC$2.compute2(RPC.java:593) [(PUBDEV-917)](https://0xdata.atlassian.net/browse/PUBDEV-917)
- h2o.glm: model summary listing same info twice [(PUBDEV-915)](https://0xdata.atlassian.net/browse/PUBDEV-915)
- Parse: Detect and reject UTF-16 encoded files [(HEXDEV-285)](https://0xdata.atlassian.net/browse/HEXDEV-285)
- DataInfo Row categorical encoding AIOOBE [(HEXDEV-283)](https://0xdata.atlassian.net/browse/HEXDEV-283)
- Fix POJO Preview exception [(github)](https://github.com/h2oai/h2o-dev/commit/d553710f66ef989dc33a86608c5cf352a7d98168)
- Fix NPE in ChunkSummary [(github)](https://github.com/h2oai/h2o-dev/commit/cd113515257ee1c493fe84616deb0643400ef32c)
- fix global name collision [(github)](https://github.com/h2oai/h2o-dev/commit/bde0b6d8fed4009367b2e2ddf999bd71cbda3b3f)
 

###Severi (0.2.2.15) - 4/25/15

####New Features 


#####Python

- added min, max, sum, median for H2OVecs and respective pyunit [(github)](https://github.com/h2oai/h2o-dev/commit/3ec14f0bfe2d045ac57b3133a7ae12ea8e70aa3c)
- added min(), max(), and sum() functionality on H2OFrames and respective pyunits [(github)](https://github.com/h2oai/h2o-dev/commit/c86cf2bfa396f38b2a035405553a1f4bb34f55c0)


#####Web UI

- View POJO in Flow [(PUBDEV-781)](https://0xdata.atlassian.net/browse/PUBDEV-781)
- help > about page or add version on main page for easy bug reporting. [(PUBDEV-804)](https://0xdata.atlassian.net/browse/PUBDEV-804)
- POJO generation: GLM [(PUBDEV-712)](https://0xdata.atlassian.net/browse/PUBDEV-712) [(github)](https://github.com/h2oai/h2o-dev/commit/35683e29e39489bc2349461e78524328e4b24e63)
- GLM model output, details in Flow [(HEXDEV-96)](https://0xdata.atlassian.net/browse/HEXDEV-96)


####Enhancements

#####Algorithms 

- K means output clean up [(HEXDEV-187)](https://0xdata.atlassian.net/browse/HEXDEV-187)
- Add FNR/TNR/FPR/TPR to threshold tables, remove recall, specificity [(github)](https://github.com/h2oai/h2o-dev/commit/1de4910b8d295b2eaa79b8e96422f45746458d92)
- Add accessor for variable importances for DL [(github)](https://github.com/h2oai/h2o-dev/commit/e11323bca7cc4e58fb2d899a3c307f42f4a8624e)
- Relax CM error tolerance for F1-optimal threshold now that AUC2 doesn't necessarily create consistent thresholds with its own CMs. [(github)](https://github.com/h2oai/h2o-dev/commit/3ab3af08e28a64acc9a406ef5ff19bf6b1c7855a)
- Added scoring history to glm [(github)](https://github.com/h2oai/h2o-dev/commit/a652ba0388784bb54f0a69f524d21f08d66eabc5)
- Added model summary to glm [(github)](https://github.com/h2oai/h2o-dev/commit/c0d221cb964a072358602b2c13fd2c33b9fa9f4b)
- Add flag to support reading data from S3N [(github)](https://github.com/h2oai/h2o-dev/commit/b4efd2c9802a8e39bc5d24ea6593e420ecfbaea9)
- Added degrees of freedom to GLM metrics schemas [(github)](https://github.com/h2oai/h2o-dev/commit/6f153381b085e94358cc0e5e317d36dce3072131)
- Allow DL scoring_history to be unlimited in length [(github)](https://github.com/h2oai/h2o-dev/commit/5485b46d240415afa3ff3e7bc8a532791ae12419)
- add plotting for binomial models [(github)](https://github.com/h2oai/h2o-dev/commit/d332e98a12bcd40ceb9714067eefce64dad97125)
- Ignore certain parameters that are not applicable (class balancing, max CM size, etc.) [(github)](https://github.com/h2oai/h2o-dev/commit/5c70787a6e43697f57c0df918bb4cdbf93d18018)
- Updated glm scoring, fill training/validation metrics in model output [(github)](https://github.com/h2oai/h2o-dev/commit/9b3cc3ec2a8f81771e0eddaf663dbfd6690dbd04)
- Rename gbm loss parameter to distribution [(github)](https://github.com/h2oai/h2o-dev/commit/d9a1e9730f3296bc125965647e5aef2ae114368c)
- Fix GBM naming: loss -> distribution [(github)](https://github.com/h2oai/h2o-dev/commit/ef93923dc83f03a9ef16ed23bb1c411bd26e067e)
- GLM LBFGS update [(github)](https://github.com/h2oai/h2o-dev/commit/3c75a2edc20b7abc9a17b9732a0bac9c7f194feb)
- na.rm for quantile is default behavior [(github)](https://github.com/h2oai/h2o-dev/commit/3ac19b6f1cb7e2a64fa6b783a19e8ddb42713caf)
- GLM update: enabled `max_predictors` in REST, updated lbfgs [(github)](https://github.com/h2oai/h2o-dev/commit/a58d515364e749b1147452a98399eb8dfadd11af)
- Remove `keep_cross_validation_splits` for now from DL [(github)](https://github.com/h2oai/h2o-dev/commit/569ae442a4905a3dbbf47a3d5c03461ce68be36a)
- Get rid of sigma in the model metrics, instead show r2 [(github)](https://github.com/h2oai/h2o-dev/commit/b12bf9496a46f25f066f3bab512cd7d81795f0f4)
- Don't show `score_every_iteration` for DL [(github)](https://github.com/h2oai/h2o-dev/commit/089aedfed90ca30e715a58363c19f3f1fe47318c)
- Don't print too large confusion matrices in Tree models [(github)](https://github.com/h2oai/h2o-dev/commit/56d51f51e5fdc5f9f25d8838003236909637b272)

#####API

- publish h2o-model.jar via REST API [(PUBDEV-779)](https://0xdata.atlassian.net/browse/PUBDEV-779)
- move all schemas and endpoints to v3 [(PUBDEV-471)](https://0xdata.atlassian.net/browse/PUBDEV-471)
- clean up routes (remove AddToNavbar, fix /Quantiles, etc) [(PUBDEV-618)](https://0xdata.atlassian.net/browse/PUBDEV-618) [(github)](https://github.com/h2oai/h2o-dev/commit/7f6eff5b47aa1e273de4710a3b26408e3516f5af)
- More data in chunk_homes call. Add num_chunks_per_vec. Add num_vec. [(github)](https://github.com/h2oai/h2o-dev/commit/635d020b2dfc45364331903c282e82e3f20d028d)
- Added chunk_homes route for frames [(github)](https://github.com/h2oai/h2o-dev/commit/1ae94079762fdbfcdd1e39d65578752860c278c6)
- Update to use /3 routes [(github)](https://github.com/h2oai/h2o-dev/commit/be422ff963bb47daf9c8e7cbcb478e6a6dbbaea5)

#####Python

- Python client should check that version number == server version number [(PUBDEV-799)](https://0xdata.atlassian.net/browse/PUBDEV-799)
- Add asfactor for month [(github)](https://github.com/h2oai/h2o-dev/commit/43c9b82ab463e712910d1353013d499684021858) 
- in Expr.show() only show 10 or less rows. remove locate from runit test because full path used [(github)](https://github.com/h2oai/h2o-dev/commit/51f4f69deba9b76837b35bf2a0b85ee2e4b20db7)
- change nulls to () [(github)](https://github.com/h2oai/h2o-dev/commit/a138cc25edc9f948d263732f665d352e44ee39c1)
- sigma is no longer part of ModelMetricsRegressionV3 [(github)](https://github.com/h2oai/h2o-dev/commit/6f2a7390ce0feb0a3d880f1bb42168642a665bb0)


#####R

- Fix integer -> int in R [(github)](https://github.com/h2oai/h2o-dev/commit/ce05247e29b5756108999689d0b10fa17edb84a8)
- add autoencoder show method [(github)](https://github.com/h2oai/h2o-dev/commit/31d70f3ddb4bad63b42ec12c8fd70b9d5745a7d1)
- accessor is $ not @ [(github)](https://github.com/h2oai/h2o-dev/commit/a43e3d6924004e34aa7b5400d149c7dab26afe70)
- add `hit_ratio_table` and `varimp` calls to R [(github)](https://github.com/h2oai/h2o-dev/commit/caa7dc001edc63928ca7a8dadba773dd25983f1d)
- add h2o.predict as alternative [(github)](https://github.com/h2oai/h2o-dev/commit/e5a48f8faaededa3fd445d4b1415665c96f1291c)
- update model output in R [(github)](https://github.com/h2oai/h2o-dev/commit/e5d101ad60c12513f2e4c7b1d16534962eb86291)


#####System

- Port MissingValueInserter EndPoint to h2o-dev. [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)
- Rapids: require a (put "key" %frame) [(PUBDEV-868)](https://0xdata.atlassian.net/browse/PUBDEV-868)
- Need pojo base model jar file embedded in h2o-dev via build process [(PUBDEV-780)](https://0xdata.atlassian.net/browse/PUBDEV-780) [(github)](https://github.com/h2oai/h2o-dev/commit/85f73202157f0ab4ee3487de8fc095951e761196)
- Make .json the default [(PUBDEV-619)](https://0xdata.atlassian.net/browse/PUBDEV-619) [(github)](https://github.com/h2oai/h2o-dev/commit/f3e88060da1a6af73940587c16fef669b1d5bbd5)
- Rename class for clarification [(github)](https://github.com/h2oai/h2o-dev/commit/89c4fe32d333940865112d8922249fc48eebe096)
- Classifies all NA columns as numeric. Also improves preview sampling accuracy by trimming partial lines at end of chunk. [(github)](https://github.com/h2oai/h2o-dev/commit/6b1cf7a180428c04cdd445974a318f5777c7f607)
- Implements sampling of files within the ParseSetup preview. This prevents poor column type guesses from only sampling the beginning of a file. [(github)](https://github.com/h2oai/h2o-dev/commit/038da7398941558656c1bda52b8429f4022c449e). 
- Rename fields `drop_na20_col` [(github)](https://github.com/h2oai/h2o-dev/commit/75131e9f1e6d1cd6788f239d72e11cf104028c3f)
- allow for many deletes as final statements in a block [(github)](https://github.com/h2oai/h2o-dev/commit/aa3e2d3ef00761ca4a4c942f33ffaf80951abc7b)
- rename initF -> init_f, dropNA20Cols -> drop_na20_cols [(github)](https://github.com/h2oai/h2o-dev/commit/e81eae78267d4981c74d866e40a48015d2086371)
- Removed tweedie param [(github)](https://github.com/h2oai/h2o-dev/commit/03902225aa912473ceb01e9cce045846949faecf)
- thresholds -> threshold [(github)](https://github.com/h2oai/h2o-dev/commit/69adcc8639c889b68ca0c97b7385a45c41d93401)
- JSON of TwoDimTable with all null values in the first column (no row headers) now doesn't have an empty column for of "" or nulls. [(github)](https://github.com/h2oai/h2o-dev/commit/de54085fe94aaa1e23aa74254fc5b8b64b85f76d)
- move H2O_Load, fix all the timezone functions [(github)](https://github.com/h2oai/h2o-dev/commit/871959887825aec1e246ae8e19e11d03db9637c5)
- Add extra verbose printout in case Frames don't match identically [(github)](https://github.com/h2oai/h2o-dev/commit/b8943f9228fe996887377f521ec135745d957033)
- allow delayed column lookup [(github)](https://github.com/h2oai/h2o-dev/commit/5060436d4d7ea7363dc74b9c0850258a38b2715a)
- add mixed type list [(github)](https://github.com/h2oai/h2o-dev/commit/99eb7106eadb0fcbe815752b181e085ba57349db)
- Added WaterMeterIo to count persist info [(github)](https://github.com/h2oai/h2o-dev/commit/2fa38aaff08584bcbf92ee2287343c2c40765d76)
- Remove special setChunkSize code in HDFS and NFS file vec [(github)](https://github.com/h2oai/h2o-dev/commit/136e7667a438a856ff06478b8ba7f6b716aced7b)
- add check for Frame on string parse [(github)](https://github.com/h2oai/h2o-dev/commit/f835768b080df1bc395bdbe0f60c2d35db8da0d8)
- Disable Memory Cleaner [(github)](https://github.com/h2oai/h2o-dev/commit/644f38f38c9f75a0008cb012c25c399a06805786)
- Handle '<' chars in Keys when swapping [(github)](https://github.com/h2oai/h2o-dev/commit/65e936912f236cacacd706bc30406f13b46acf7e)
- allow for colnames in slicing [(github)](https://github.com/h2oai/h2o-dev/commit/947e6cc1f0becb58a5d36387a6500b303293c6a8)
- Adjusts parse type detection. If column is all one string value, declare it an enum [(github)](https://github.com/h2oai/h2o-dev/commit/08e7845b786c445862554d4f4c5dac7c78204284)

#####Web UI

- nice algo names in the Flow dropdown (full word names) [(PUBDEV-707)](https://0xdata.atlassian.net/browse/PUBDEV-707)
- Compute and Display Hit Ratios [(PUBDEV-630)](https://0xdata.atlassian.net/browse/PUBDEV-630)
- Limit POJO preview to 1000 lines [(github)](https://github.com/h2oai/h2o-dev/commit/ce82fe74da9641d72c47dabd03514c7402998f76) 


####Bug Fixes


#####Algorithms

- GLM: lasso i.e alpha =1 seems to be giving wrong answers [(PUBDEV-769)](https://0xdata.atlassian.net/browse/PUBDEV-769)
- AUC: h2o reports .5 auc when actual auc is 1 [(PUBDEV-879)](https://0xdata.atlassian.net/browse/PUBDEV-879)
- h2o.glm: No output displayed for the model [(PUBDEV-858)](https://0xdata.atlassian.net/browse/PUBDEV-858)
- h2o.glm model object output needs a fix [(PUBDEV-815)](https://0xdata.atlassian.net/browse/PUBDEV-815)
- h2o.glm model object says : fill me in GLMModelOutputV2; I think I'm redundant [1] FALSE [(PUBDEV-765)](https://0xdata.atlassian.net/browse/PUBDEV-765)
- GLM : Build GLM Model => Java Assertion error [(PUBDEV-686)](https://0xdata.atlassian.net/browse/PUBDEV-686)
- GLM :=> Progress shows -100% [(PUBDEV-861)](https://0xdata.atlassian.net/browse/PUBDEV-861)
- GBM: Negative sign missing in initF value for ad dataset [(PUBDEV-880)](https://0xdata.atlassian.net/browse/PUBDEV-880)
- K-Means takes a validation set but doesn't use it [(PUBDEV-826)](https://0xdata.atlassian.net/browse/PUBDEV-826)
- Absolute_MCC is NaN (sometimes) [(PUBDEV-848)](https://0xdata.atlassian.net/browse/PUBDEV-848) [(github)](https://github.com/h2oai/h2o-dev/commit/4480f22b6b3a38abb776339bee506b356f589c90)
- GBM: A proper error msg should be thrown when the user sets the max depth =0 [(PUBDEV-838)](https://0xdata.atlassian.net/browse/PUBDEV-838) [(github)](https://github.com/h2oai/h2o-dev/commit/df77f3de5e8940f3598af67d520f185d1e478ec4)
- DRF Regression Assertion Error [(PUBDEV-824)](https://0xdata.atlassian.net/browse/PUBDEV-824)
- h2o.randomForest: if h2o is not returning the mse for the 0th tree then it should not be reported in the model object [(PUBDEV-811)](https://0xdata.atlassian.net/browse/PUBDEV-811)
- GBM: Got exception `class java.lang.AssertionError` with msg `null` java.lang.AssertionError at hex.tree.gbm.GBM$GBMDriver$GammaPass.map [(PUBDEV-693)](https://0xdata.atlassian.net/browse/PUBDEV-693)
- GBM: Got exception `class java.lang.AssertionError` with msg `null` java.lang.AssertionError at hex.ModelMetricsMultinomial$MetricBuildMultinomial.perRow [(HEXDEV-248)](https://0xdata.atlassian.net/browse/HEXDEV-248)
- GBM get java.lang.AssertionError: Coldata 2199.0 out of range C17:5086.0-19733.0 step=57.214844 nbins=256 isInt=1 [(HEXDEV-241)](https://0xdata.atlassian.net/browse/HEXDEV-241)
- GLM: glmnet objective function better than h2o.glm [(PUBDEV-749)](https://0xdata.atlassian.net/browse/PUBDEV-749)
- GLM: get AIOOB:-36 at hex.glm.GLMTask$GLMIterationTask.postGlobal(GLMTask.java:733) [(PUBDEV-894)](https://0xdata.atlassian.net/browse/PUBDEV-894) [(github)](https://github.com/h2oai/h2o-dev/commit/5bba2df2e208a0a7c7fd19732971575eb9dc2259)
- Fixed glm behavior in case no rows are left after filtering out NAs [(github)](https://github.com/h2oai/h2o-dev/commit/57dc0f3a168ed835c48aa29f6e0d6322c6a5523a)
- Fix memory leak in validation scoring in K-Means [(github)](https://github.com/h2oai/h2o-dev/commit/f3f01e4dfe66e0181df0ff85a2a9a108295df94c)

#####API

- API unification: DataFrame should be able to accept URI referencing file on local filesystem [(PUBDEV-709)](https://0xdata.atlassian.net/browse/PUBDEV-709) [(github)](https://github.com/h2oai/h2o-dev/commit/a72e77388c0f7b17e4595482f9afe42f14055ce9)


#####Python 

- Python: describe returning all zeros [(PUBDEV-875)](https://0xdata.atlassian.net/browse/PUBDEV-875)
- python/R & merge() [(PUBDEV-834)](https://0xdata.atlassian.net/browse/PUBDEV-834)
- python Expr min, max, median, sum bug [(PUBDEV-845)](https://0xdata.atlassian.net/browse/PUBDEV-845) [(github)](https://github.com/h2oai/h2o-dev/commit/7839efd5899366a3b51ef79156717a718ab01c38)




#####R

- (R and Python) clients must not pass response to DL AutoEncoder model builder [(PUBDEV-897)](https://0xdata.atlassian.net/browse/PUBDEV-897) [(github)](https://github.com/h2oai/h2o-dev/commit/bc78ecfa5e0c37cebd55ed9ba7b3ae6163ebdc66)
- h2o.varimp, h2o.hit_ratio_table missing in R [(PUBDEV-842)](https://0xdata.atlassian.net/browse/PUBDEV-842)
- GLM: No help for h2o.glm from R [(PUBDEV-732)](https://0xdata.atlassian.net/browse/PUBDEV-732)
- h2o.confusionMatrix not working for binary response [(PUBDEV-782)](https://0xdata.atlassian.net/browse/PUBDEV-782) [(github)](https://github.com/h2oai/h2o-dev/commit/a834cbc80a62062c55456233ce27ba5e9c3a87a3)
- h2o.splitframe complains about destination keys [(PUBDEV-783)](https://0xdata.atlassian.net/browse/PUBDEV-783)
- h2o.assign does not work [(PUBDEV-784)](https://0xdata.atlassian.net/browse/PUBDEV-784) [(github)](https://github.com/h2oai/h2o-dev/commit/b007c0b59dbb03716571384adb3271fbe8385a55)
- H2oR: should display only first few entries of the variable importance in model object [(PUBDEV-850)](https://0xdata.atlassian.net/browse/PUBDEV-850)
- R: h2o.confusion matrix needs formatting [(PUBDEV-764)](https://0xdata.atlassian.net/browse/PUBDEV-764)
- R: h2o.confusionMatrix => No Confusion Matrices for H2ORegressionMetrics [(PUBDEV-710)](https://0xdata.atlassian.net/browse/PUBDEV-710)
- h2o.deeplearning: model object output needs a fix [(PUBDEV-821)](https://0xdata.atlassian.net/browse/PUBDEV-821)
- h2o.varimp, h2o.hit_ratio_table missing in R [(PUBDEV-842)](https://0xdata.atlassian.net/browse/PUBDEV-842) 
- force gc more frequently [(github)](https://github.com/h2oai/h2o-dev/commit/0db9a3716ecf573ef4b3c71ec1116cc8b27e62c6)

#####System

- MapR FS loads are too slow [(PUBDEV-927)](https://0xdata.atlassian.net/browse/PUBDEV-927)
- ensure that HDFS works from Windows [(PUBDEV-812)](https://0xdata.atlassian.net/browse/PUBDEV-812)
- Summary: on a time column throws,'null' is not an object (evaluating 'column.domain[level.index]') in Flow [(PUBDEV-867)](https://0xdata.atlassian.net/browse/PUBDEV-867)
- Parse: An enum column gets parsed as int for the attached file [(PUBDEV-606)](https://0xdata.atlassian.net/browse/PUBDEV-606)
- Parse => 40Mx1_uniques => class java.lang.RuntimeException [(PUBDEV-729)](https://0xdata.atlassian.net/browse/PUBDEV-729)
- if there are fewer than 5 unique values in a dataset column, mins/maxs reports e+308 values [(PUBDEV-150)](https://0xdata.atlassian.net/browse/PUBDEV-150) [(github)](https://github.com/h2oai/h2o-dev/commit/49c966791a146687039350689bc09cee10f38820)
- Sparkling water - `DataFrame[T_UUID]` to `SchemaRDD[StringType]` [(PUDEV-771)](https://0xdata.atlassian.net/browse/PUBDEV-771) 
- Sparkling water - `DataFrame[T_NUM(Long)]` to `SchemaRDD[LongType]` [(PUBDEV-767)](https://0xdata.atlassian.net/browse/PUBDEV-767)
- Sparkling water - `DataFrame[T_ENUM]` to `SchemaRDD[StringType]` [(PUBDEV-766)](https://0xdata.atlassian.net/browse/PUBDEV-766)
- Inconsistency in row and col slicing [(HEXDEV-265)](https://0xdata.atlassian.net/browse/HEXDEV-265) [(github)](https://github.com/h2oai/h2o-dev/commit/edd8923a438282e3c24d086e1a03b88471d58114)
- rep_len expects literal length only [(HEXDEV-268)](https://0xdata.atlassian.net/browse/HEXDEV-268) [(github)](https://github.com/h2oai/h2o-dev/commit/1783a889a54d2b23da8bd8ec42774f52efbebc60)
- cbind and = don't work within a single rapids block [(HEXDEV-237)](https://0xdata.atlassian.net/browse/HEXDEV-237)
- Rapids response for c(value) does not have frame key [(HEXDEV-252)](https://0xdata.atlassian.net/browse/HEXDEV-252)
- S3 parse takes forever [(PUBDEV-876)](https://0xdata.atlassian.net/browse/PUBDEV-876)
- Parse => Enum unification fails in multi-node parse [(PUBDEV-718)](https://0xdata.atlassian.net/browse/PUBDEV-718) [(github)](https://github.com/h2oai/h2o-dev/commit/0db8c392070583f32849447b65784da18197c14d)
- All nodes are not getting updated with latest status of each other nodes info [(PUBDEV-768)](https://0xdata.atlassian.net/browse/PUBDEV-768)
- Cluster creation is sometimes rejecting new nodes (post jenkins-master-1128+) [(PUBDEV-807)](https://0xdata.atlassian.net/browse/PUBDEV-807)
- Parse => Multiple files 1 zip/ 1 csv gives Array index out of bounds [(PUBDEV-840)](https://0xdata.atlassian.net/browse/PUBDEV-840)
- Parse => failed for X5MRows6KCols ==> OOM => Cluster dies [(PUBDEV-836)](https://0xdata.atlassian.net/browse/PUBDEV-836)
- /frame/foo pagination weirded out [(HEXDEV-277)](https://0xdata.atlassian.net/browse/HEXDEV-277) [(github)](https://github.com/h2oai/h2o-dev/commit/c40da923d97720466fb372758d66509aa628e97c)
- Removed code that flipped enums to strings [(github)](https://github.com/h2oai/h2o-dev/commit/7d56bcee73cf3c90b498cadf8601610e5f145dbc)




#####Web UI

- Flow: It would be really useful to have the mse plots back in GBM [(PUBDEV-889)](https://0xdata.atlassian.net/browse/PUBDEV-889)
- State change in Flow is not fully validated [(PUBDEV-919)](https://0xdata.atlassian.net/browse/PUBDEV-919)
- Flows : Not able to load saved flows from hdfs [(PUBDEV-872)](https://0xdata.atlassian.net/browse/PUBDEV-872)
- Save Function in Flow crashes [(PUBDEV-791)](https://0xdata.atlassian.net/browse/PUBDEV-791) [(github)](https://github.com/h2oai/h2o-dev/commit/ad724bf7af86180d7045a99790602bd52908945f)
- Flow: should throw a proper error msg when user supplied response have more categories than algo can handle [(PUBDEV-866)](https://0xdata.atlassian.net/browse/PUBDEV-866)
- Flow display of a summary of a column with all missing values fails. [(HEXDEV-230)](https://0xdata.atlassian.net/browse/HEXDEV-230)
- Split frame UI improvements [(HEXDEV-275)](https://0xdata.atlassian.net/browse/HEXDEV-275)
- Flow : Decimal point precisions to be consistent to 4 as in h2o1 [(PUBDEV-844)](https://0xdata.atlassian.net/browse/PUBDEV-844)
- Flow: Prediction frame is outputing junk info [(PUBDEV-825)](https://0xdata.atlassian.net/browse/PUBDEV-825)
- EC2 => Cluster of 16 nodes => Water Meter => shows blank page [(PUBDEV-831)](https://0xdata.atlassian.net/browse/PUBDEV-831)
- Flow: Predict - "undefined is not an object (evaluating `prediction.thresholds_and_metric_scores.name`) [(PUBDEV-559)](https://0xdata.atlassian.net/browse/PUBDEV-559)
- Flow: inspect getModel for PCA returns error [(PUBDEV-610)](https://0xdata.atlassian.net/browse/PUBDEV-610)
- Flow, RF: Can't get Predict results; "undefined is not an object (evaluating `prediction.confusion_matrices.length`)" [(PUBDEV-695)](https://0xdata.atlassian.net/browse/PUBDEV-695)
- Flow, GBM: getModel is broken -Error processing GET /3/Models.json/gbm-b1641e2dc3-4bad-9f69-a5f4b67051ba null is not an object (evaluating `source.length`) [(PUBDEV-800)](https://0xdata.atlassian.net/browse/PUBDEV-800) 






###Severi (0.2.2.1) - 4/10/15

####New Features 

#####R

- Implement /3/Frames/<my_frame>/summary [(PUBDEV-6)](https://0xdata.atlassian.net/browse/PUBDEV-6) [(github)](https://github.com/h2oai/h2o-dev/commit/07bc295e1687d88e40d8391ea78f91aff4183a6f)
- add allparameters slot to allow default values to be shown [(github)](https://github.com/h2oai/h2o-dev/commit/9699a4c43ce4936dbc3019c75b2a36bd1ef22b45)
- add log loss accessor [(github)](https://github.com/h2oai/h2o-dev/commit/22ace748ae4004305ae9edb04f17141d0dbd87d4)


####Enhancements

#####Algorithms

- POJO generation: GBM [(PUBDEV-713)](https://0xdata.atlassian.net/browse/PUBDEV-713)
- POJO generation: DRF [(PUBDEV-714)](https://0xdata.atlassian.net/browse/PUBDEV-714)
- Compute and Display Hit Ratios [(PUBDEV-630)](https://0xdata.atlassian.net/browse/PUBDEV-630) [(github)](https://github.com/h2oai/h2o-dev/commit/04b13f2fb05b752dbd04121f50845bebcb6f9955)
- Add DL POJO scoring [(PUBDEV-585)](https://0xdata.atlassian.net/browse/PUBDEV-585)
- Allow validation dataset for AutoEncoder [(PUDEV-581)](https://0xdata.atlassian.net/browse/PUBDEV-581)
- PUBDEV-580: Add log loss to binomial and multinomial model metric [(github)](https://github.com/h2oai/h2o-dev/commit/8982a0a1ba575bd5ca6ca3e854382e03146743cd)
- Port MissingValueInserter EndPoint to h2o-dev [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)
- increase tolerance to 2e-3 (was 1e-3 ..failed with 0.001647 relative difference [(github)](https://github.com/h2oai/h2o-dev/commit/9ce26530cc7d4d4aef55b5e0debc978bacc8ac78)
- change tolerance to 1e-3 [(github)](https://github.com/h2oai/h2o-dev/commit/bb5aa7806d37e1148029ef848a8df0d7a28cba2a)
- Add option to export weights and biases to REST API / Flow. [(github)](https://github.com/h2oai/h2o-dev/commit/2f711045f2678622a7d6d44f7210adb74a513ce6)
- Add scree plot for H2O PCA models and fix Runit test. [(github)](https://github.com/h2oai/h2o-dev/commit/5743019075e023590019fab9a4da8c09500643a0)
- Remove quantiles from the model builders list. [(github)](https://github.com/h2oai/h2o-dev/commit/6283dfbc626cb2b9a65df2f4b90a87371ef5c752)
- GLM update: added row filtering argument to line search task, fixed issues with dfork/asyncExec [(github)](https://github.com/h2oai/h2o-dev/commit/7492ed95915a85121f0042b5800d58bda2805a87)
- Updated rho-setting in GLM. [(github)](https://github.com/h2oai/h2o-dev/commit/a130fd6abbd13fff44e0eb813d31cc04afcedef7)
- No threshold 0.5; use the default (max F1) instead [(github)](https://github.com/h2oai/h2o-dev/commit/e56425d6f83aa0e1dc523acc3ed4b5a49d0223fc)
- GLM update: updated initilization, NA row filtering, default lambda is now empty, will be picked based on the fraction of lambda_max. [(github)](https://github.com/h2oai/h2o-dev/commit/04a3f8e496c00de9e35c8ee33a6d3ddb8466a3d8)
- Updated ADMM solver. [(github)](https://github.com/h2oai/h2o-dev/commit/1a6ef44a24463b2538731065fc39eef4531e062e)
- Added makeGLMModel call. [(github)](https://github.com/h2oai/h2o-dev/commit/9792ff032356982915d814c7918c48582bf3ffea)
- Start with classification error NaN at t=0 for DL, not with 1. [(github)](https://github.com/h2oai/h2o-dev/commit/c33ca1f385844c90c473fe2941bbb8b2c2ab663f)
- Relax DL POJO relative tolerance to 1e-2. [(github)](https://github.com/h2oai/h2o-dev/commit/f7a2fe37845c00980a23f8e68b34ad044fa647e2)
- Override nfeatures() method in DLModelOutput. [(github)](https://github.com/h2oai/h2o-dev/commit/7c6bcf844c8e162b8fb16ee1f7e208717b82d606)
- Renaming of fields in GLM [(github)](https://github.com/h2oai/h2o-dev/commit/d21180ab5ea973848d4cdcb896c32400c3d77d38)
- GLM: Take out Balance Classes [(PUBDEV-795)](https://0xdata.atlassian.net/browse/PUBDEV-795)



#####API

- schema metadata for Map fields should include the key and value types [(PUBDEV-753)](https://0xdata.atlassian.net/browse/PUBDEV-753) [(github)](https://github.com/h2oai/h2o-dev/commit/4b55db36f259740043b8418e23e298fb0ed5a43d)
- schema metadata should include the superclass [(PUBDEV-754)](https://0xdata.atlassian.net/browse/PUBDEV-754)
- rest api naming convention: n_folds vs ntrees [(PUBDEV-737)](https://0xdata.atlassian.net/browse/PUBDEV-737)
- schema metadata for Map fields should include the key and value types [(PUBDEV-753)](https://0xdata.atlassian.net/browse/PUBDEV-753)
- Create REST Endpoint for exposing .java pojo models [(PUBDEV-778)](https://0xdata.atlassian.net/browse/PUBDEV-778)






#####Python

- Run GLM from Python (including LBFGS) [(HEXDEV-92)](https://0xdata.atlassian.net/browse/HEXDEV-92)
- added H2OFrame show(), as_list(), and slicing pyunits [(github)](https://github.com/h2oai/h2o-dev/commit/b1febc33faa336924ffdb416d8d4a3cb8bba37fa)
- changed solver parameter to "L_BFGS" [(github)](https://github.com/h2oai/h2o-dev/commit/93e71509bcfa0e76d344819214a08b944ccbfb89)
- added multidimensional slicing of H2OFrames and Exprs. [(github)](https://github.com/h2oai/h2o-dev/commit/7d9be09ff0b68f92e46a0c7336dcf8134d026b88)
- add h2o.groupby to python interface [(github)](https://github.com/h2oai/h2o-dev/commit/aee9522f0c7edbd960ded78f5ba01daf6d54925b)
- added H2OModel.confusionMatrix() to return confusion matrix of a prediction [(github)](https://github.com/h2oai/h2o-dev/commit/6e6bc378f3a10c094752470de786be600a0a98b3)





#####R

- PUBDEV-578, PUBDEV-541, PUBDEV-566.
	-R client now sends the data frame column names and data types to ParseSetup.
	-R client can get column names from a parsed frame or a list.
	-Respects client request for column data types [(github)](https://github.com/h2oai/h2o-dev/commit/ba063be25d3fbb658b016ff514083284e2d95d78)
- R: Cannot create new columns through R [(PUBDEV-571)](https://0xdata.atlassian.net/browse/PUBDEV-571)
- H2O-R: it would be more useful if h2o.confusion matrix reports the actual class labels instead of [,1] and [,2] [(PUBDEV-553)](https://0xdata.atlassian.net/browse/PUBDEV-553)
- Support both multinomial and binomial CM [(github)](https://github.com/h2oai/h2o-dev/commit/4ad2ed007635a7e8c2fd4fb0ae985cf00a81df15)



#####System

- Flow: Standardize `max_iters`/`max_iterations` parameters [(PUBDEV-447)](https://0xdata.atlassian.net/browse/PUBDEV-447) [(github)](https://github.com/h2oai/h2o-dev/commit/6586f1f2f233518a7ee6179ec2bc19d9d7b61d15)
- Add ERROR logging level for too-many-retries case [(PUBDEV-146)](https://0xdata.atlassian.net/browse/PUBDEV-146) [(github)](https://github.com/h2oai/h2o-dev/commit/ae5bdf26453643b58403a6a4fb136259ac9acd6b)
- Simplify checking of cluster health. Just report the status immediately. [(github)](https://github.com/h2oai/h2o-dev/commit/25fde3914460e7572cf3500f236d43e50a502aab)
- reduce timeout [(github)](https://github.com/h2oai/h2o-dev/commit/4c93ddfd92801fdef60961d44ccb7cf512f37a90)
- strings can have ' or " beginning [(github)](https://github.com/h2oai/h2o-dev/commit/034243f094ae67fb15e8d575146f6e64c8727d39)
- Throw a validation error in flow if any training data cols are non-numeric [(github)](https://github.com/h2oai/h2o-dev/commit/091c18331f19a5a1db8b3eb0b000ca72abd29f81)
- Add getHdfsHomeDirectory(). [(github)](https://github.com/h2oai/h2o-dev/commit/68c3f730576c21bd1191f8af9dd7fd9445b89f83)
- Added --verbose.  [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)


#####Web UI

- PUBDEV-707: nice algo names in the Flow dropdown (full word names) [(github)](https://github.com/h2oai/h2o-dev/commit/ab87c26ae8ac17691034f4d9014ee17ba2168d89)
- Unbreak Flow's ConfusionMatrix display. [(github)](https://github.com/h2oai/h2o-dev/commit/45911f2ff28e2357d5545ac23135f090c10f13e0)
- POJO generation: DL [(PUBDEV-715)](https://0xdata.atlassian.net/browse/PUBDEV-715)



####Bug Fixes


#####Algorithms

- GLM : Build GLM model with nfolds brings down the cloud => FATAL: unimplemented [(PUBDEV-731)](https://0xdata.atlassian.net/browse/PUBDEV-731) [(github)](https://github.com/h2oai/h2o-dev/commit/79123971fdea5660355f57de4e9a02d3712250b1)
- DL : Build DL Model => FATAL: unimplemented: n_folds >= 2 is not (yet) implemented => SHUTSDOWN CLOUD [(PUBDEV-727)](https://0xdata.atlassian.net/browse/PUBDEV-727) [(github)](https://github.com/h2oai/h2o-dev/commit/6f59755f28c3fc3cee549630bb5e22a985d185ab)
- GBM => Build GBM model => No enum constant  hex.tree.gbm.GBMModel.GBMParameters.Family.AUTO [(PUBDEV-723)](https://0xdata.atlassian.net/browse/PUBDEV-723)
- GBM: When run with loss = auto with a numeric column get- error :No enum constant hex.tree.gbm.GBMModel.GBMParameters.Family.AUTO
 [(PUBDEV-708)](https://0xdata.atlassian.net/browse/PUBDEV-708) [(github)](https://github.com/h2oai/h2o-dev/commit/15d5b5a6108d165f230a856aa3c38a4eb158ee93)
- gbm: does not complain when min_row >dataset size [(PUBDEV-694)](https://0xdata.atlassian.net/browse/PUBDEV-694) [(github)](https://github.com/h2oai/h2o-dev/commit/a3d9d1cca2aa070c536084ca1bb90eecfbf609e7)
- GLM: reports wrong residual degrees of freedom [(PUBDEV-668)](https://0xdata.atlassian.net/browse/PUBDEV-668) 
- H2O dev reports less accurate aucs than H2O [(PUBDEV-602)](https://0xdata.atlassian.net/browse/PUBDEV-602)
- GLM : Build GLM model fails => ArrayIndexOutOfBoundsException [(PUBDEV-601)](https://0xdata.atlassian.net/browse/PUBDEV-601)
- divide by zero in modelmetrics for deep learning [(PUBDEV-568)](https://0xdata.atlassian.net/browse/PUBDEV-568)
- GBM: reports 0th tree mse value for the validation set, different than the train set ,When only train sets is provided [(PUDEV-561)](https://0xdata.atlassian.net/browse/PUBDEV-561)
- GBM: Initial mse in bernoulli seems to be off [(PUBDEV-515)](https://0xdata.atlassian.net/browse/PUBDEV-515) 
- GLM : Build Model fails with Array Index Out of Bound exception [(PUBDEV-454)](https://0xdata.atlassian.net/browse/PUBDEV-454) [(github)](https://github.com/h2oai/h2o-dev/commit/78773be9f40e1403457e42378baf0d1aeaf3e32d)
- Custom Functions don't work in apply() in R [(PUBDEV-436)](https://0xdata.atlassian.net/browse/PUBDEV-436)
- GLM failure: got NaNs and/or Infs in beta on airlines [(PUBDEV-362)](https://0xdata.atlassian.net/browse/PUBDEV-362)
- MetricBuilderMultinomial.perRow AssertionError while running GBM [(HEXDEV-240)](https://0xdata.atlassian.net/browse/HEXDEV-240)
- Problems during Train/Test adaptation between Enum/Numeric [(HEXDEV-229)](https://0xdata.atlassian.net/browse/HEXDEV-229)
- DRF/GBM balance_classes=True throws unimplemented exception [(HEXDEV-226)](https://0xdata.atlassian.net/browse/HEXDEV-226) [(github)](https://github.com/h2oai/h2o-dev/commit/3a4f7ee3fdb159187b5ae1789d55752192d893e6)
- AUC reported on training data is 0, but should be 1 [(HEXDEV-223)](https://0xdata.atlassian.net/browse/HEXDEV-223) [(github)](https://github.com/h2oai/h2o-dev/commit/312558524749a0b28bf22ffd8c34ebcd6996b350)
- glm pyunit intermittent failure [(HEXDEV-199)](https://0xdata.atlassian.net/browse/HEXDEV-199)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(HEXDEV-194)](https://0xdata.atlassian.net/browse/HEXDEV-194)
- get rid of nfolds= param since it's not supported in GLM yet [(￼github)](https://github.com/h2oai/h2o-dev/commit/8603ad35d4243ef598acadbfaa084c6852acd7ce)
- Fixed degrees of freedom (off by 1) in glm, added test. [(github)](https://github.com/h2oai/h2o-dev/commit/09e6d6f5222c40cb73f28c6df4e30d92b98f8361)
- GLM fix: fix filtering of rows with NAs and fix in sparse handling. [(github)](https://github.com/h2oai/h2o-dev/commit/5bad9b5c7bc2a3a4d4a2496ade7194a0438f17d9)
- Fix GLM job fail path to call Job.fail(). [(github)](https://github.com/h2oai/h2o-dev/commit/912663fb0e05b4670d014a0a4c7bff03410c467e)
- Full AUC computation, bug fixes [(github)](https://github.com/h2oai/h2o-dev/commit/9124cc321defb0b4defba7bef02cf387ff238c28)
- Fix ADMM for upper/lower bounds. (updated rho settings + update u-vector in ADMM for intercept) [(github)](https://github.com/h2oai/h2o-dev/commit/47a09ffe2271db050bd6d8042dfeaa40c4874b8a)
- Few glm fixes [(github)](https://github.com/h2oai/h2o-dev/commit/04a344ebede1f34b58e9aa82889bac1af9bd5f47)
- DL : KDD Algebra data set => Build DL model => ArrayIndexOutOfBoundsException [(PUBDEV-696)](https://0xdata.atlassian.net/browse/PUBDEV-696)
- GBm: Dev vs H2O for depth 5, minrow=10, on prostate, give different trees [(PUBDEV-759)](https://0xdata.atlassian.net/browse/PUBDEV-759)
- GBM param min_rows doesn't throw exception for negative values [(PUBDEV-697)](https://0xdata.atlassian.net/browse/PUBDEV-697)
- GBM : Build GBM Model => Too many levels in response column! (java.lang.IllegalArgumentException) => Should display proper error message [(PUBDEV-698)](https://0xdata.atlassian.net/browse/PUBDEV-698)
- GBM:Got exception 'class java.lang.AssertionError', with msg 'Something is wrong with GBM trees since returned prediction is Infinity [(PUBDEV-722)](https://0xdata.atlassian.net/browse/PUBDEV-722)






#####API

- Cannot adapt numeric response to factors made from numbers [(PUBDEV-620)](https://0xdata.atlassian.net/browse/PUBDEV-620)
- not specifying response\_column gets NPE (deep learning build_model()) I think other algos might have same thing [(PUBDEV-131)](https://0xdata.atlassian.net/browse/PUBDEV-131)
- NPE response has null msg, exception\_msg and dev\_msg [(HEXDEV-225)](https://0xdata.atlassian.net/browse/HEXDEV-225)
- Flow :=> Save Flow => On Mac and Windows 8.1 => NodePersistentStorage failure while attempting to overwrite (?) a flow [(HEXDEV-202)](https://0xdata.atlassian.net/browse/HEXDEV-202) [(github)](https://github.com/h2oai/h2o-dev/commit/db710a4dc7dda4570f5b87cb9e386be6c76f001e)
- the can_build field in ModelBuilderSchema needs values[] to be set [(PUBDEV-755)](https://0xdata.atlassian.net/browse/PUBDEV-755)
- value field in the field metadata isn't getting serialized as its native type [(PUBDEV-756)](https://0xdata.atlassian.net/browse/PUBDEV-756)


#####Python
- python api asfactor() on -1/1 column issue [(HEXDEV-203)](https://0xdata.atlassian.net/browse/HEXDEV-203)


#####R
- Rapids: Operations %/% and %% returns Illegal Argument Exception in R [(PUBDEV-736)](https://0xdata.atlassian.net/browse/PUBDEV-736)
- quantile: H2oR displays wrong quantile values when call the default quantile without specifying the probs [(PUBDEV-689)](https://0xdata.atlassian.net/browse/PUBDEV-689)[(github)](https://github.com/h2oai/h2o-dev/commit/9ef5e2befe08a5ff7ce13e8b4b39acf7171e8a1f)
- as.factor: If a user reruns as.factor on an already factor column, h2o should not show an exception [(PUBDEV-622)](https://0xdata.atlassian.net/browse/PUBDEV-622)
- as.factor works only on positive integers [(PUBDEV-617)](https://0xdata.atlassian.net/browse/PUBDEV-617) [(github)](https://github.com/h2oai/h2o-dev/commit/08f3acb62bec0f2c3808841d6b7f8d1382f616f0)
- H2O-R: model detail lists three mses, the first MSE slot does not contain any info about the model and hence, should be removed from the model details [(PUBDEV-605)](https://0xdata.atlassian.net/browse/PUBDEV-605) [(github)](https://github.com/h2oai/h2o-dev/commit/55f975d551432114a0088d19bd2397894410dd94)
- H2O-R: Strings: While slicing get Error From H2O: water.DException$DistributedException [(PUBDEV-592)](https://0xdata.atlassian.net/browse/PUBDEV-592)
- R: h2o.confusionMatrix should handle both models and model metric objects [(PUBDEV-590)](https://0xdata.atlassian.net/browse/PUBDEV-590)
- R: as.Date not functional with H2O objects [(PUBDEV-583)](https://0xdata.atlassian.net/browse/PUBDEV-583) [(github)](https://github.com/h2oai/h2o-dev/commit/f2f64b1ed29c8d7ab47252d84d8634240b3889d0)
- R: some apply functions don't work on H2OFrame objects [(PUBDEV-579)](https://0xdata.atlassian.net/browse/PUBDEV-579) [(github)](https://github.com/h2oai/h2o-dev/commit/10f1245dbbc5ac36024e8ce51932dd991ff50688)
- h2o.confusionMatrices for multinomial does not work [(PUBDEV-577)](https://0xdata.atlassian.net/browse/PUBDEV-577)
- R: slicing issues [(PUBDEV-573)](https://0xdata.atlassian.net/browse/PUBDEV-573)
- R: length and is.factor don't work in h2o.ddply [(PUBDEV-572)](https://0xdata.atlassian.net/browse/PUBDEV-572) [(github)](https://github.com/h2oai/h2o-dev/commit/bdc55a95a91af784a8b4497bbc8e4835fa1049bf)
- R: apply(hex, c(1,2), ...) doesn't properly raise an error [(PUBDEV-570)](https://0xdata.atlassian.net/browse/PUBDEV-570) [(github)](https://github.com/h2oai/h2o-dev/commit/75ddf7f82b4acabe77d0928b66ea7a51dbc5a8b4)
- R: Slicing negative indices to negative indices fails [(PUBDEV-569)](https://0xdata.atlassian.net/browse/PUBDEV-569) [(github)](https://github.com/h2oai/h2o-dev/commit/bf6620f70a3f09a8a57d2da563188c342d67aeb7)
- h2o.ddply: doesn't accept anonymous functions [(PUBDEV-567)](https://0xdata.atlassian.net/browse/PUBDEV-567) [(github)](https://github.com/h2oai/h2o-dev/commit/3c3c4e7134fe03e5a8a5cdd8530f59094264b7f3)
- ifelse() cannot return H2OFrames in R [(PUBDEV-543)](https://0xdata.atlassian.net/browse/PUBDEV-543)
- as.h2o loses track of headers [(PUBDEV-541)](https://0xdata.atlassian.net/browse/PUBDEV-541)
- H2O-R not showing meaningful error msg [(PUBDEV-502)](https://0xdata.atlassian.net/browse/PUBDEV-502)
- H2O.fail() had better fail [(PUBDEV-470)](https://0xdata.atlassian.net/browse/PUBDEV-470) [(github)](https://github.com/h2oai/h2o-dev/commit/16939a831a315c5f7ec221bc15fad5826fd4c677)
- fix issue in toEnum [(github)](https://github.com/h2oai/h2o-dev/commit/99fe517a00f54dea9ca4e64054c06a6e8cd1ea8c)
- fix colnames and new col creation [(github)](https://github.com/h2oai/h2o-dev/commit/61000a75eaa3b9a92dced1c66ecdce687cef64b2)
- R: h2o.init() is posting warning messages of an unhealthy cluster when the cluster is fine. [(PUBDEV-734)](https://0xdata.atlassian.net/browse/PUBDEV-734)
- h2o.split frame is failing [(PUBDEV-560)](https://0xdata.atlassian.net/browse/PUBDEV-560)





#####System

- key type failure should fail the request, not the cloud [(PUBDEV-739)](https://0xdata.atlassian.net/browse/PUBDEV-739) [(github)](https://github.com/h2oai/h2o-dev/commit/52ebdf0cd6d972acb15c8cf315e2d1105c5b1703)
- Parse => Import Medicare supplier file => Parse = > Illegal argument for field: column_names of schema: ParseV2: string and key arrays' values must be quoted, but the client sent: " [(PUBDEV-719)](https://0xdata.atlassian.net/browse/PUBDEV-719)
- Overwriting a constant vector with strings fails [(PUBDEV-702)](https://0xdata.atlassian.net/browse/PUBDEV-702)
- H2O - gets stuck while calculating quantile,no error msg, just keeps running a job that normally takes less than a sec [(PUBDEV-685)](https://0xdata.atlassian.net/browse/PUBDEV-685)
- Summary and quantile on a column with all missing values should not throw an exception [(PUBDEV-673)](https://0xdata.atlassian.net/browse/PUBDEV-673) [(github)](https://github.com/h2oai/h2o-dev/commit/7acd14a7d6bbdfa5ab6a7c2e8c2987622b229603)
- View Logs => class java.lang.RuntimeException: java.lang.IllegalArgumentException: File /home2/hdp/yarn/usercache/neeraja/appcache/application_1427144101512_0039/h2ologs/h2o_172.16.2.185_54321-3-info.log does not exist [(PUBDEV-600)](https://0xdata.atlassian.net/browse/PUBDEV-600)
- Parse: After parsing Chicago crime dataset => Not able to build models or Get frames [(PUBDEV-576)](https://0xdata.atlassian.net/browse/PUBDEV-576)
- Parse: Numbers completely parsed wrong [(PUBDEV-574)](https://0xdata.atlassian.net/browse/PUBDEV-574)
- Flow: converting a column to enum while parsing does not work [(PUBDEV-566)](https://0xdata.atlassian.net/browse/PUBDEV-566)
- Parse: Fail gracefully when asked to parse a zip file with different files in it [(PUBDEV-540)](https://0xdata.atlassian.net/browse/PUBDEV-540)[(github)](https://github.com/h2oai/h2o-dev/commit/23a60d68e9d77fe07ae9d940b0ebb6636ef40ee3)
- toDataFrame doesn't support sequence format schema (array, vectorUDT) [(PUBDEV-457)](https://0xdata.atlassian.net/browse/PUBDEV-457)
- Parse : Parsing random crap gives java.lang.ArrayIndexOutOfBoundsException: 13 [(PUBDEV-428)](https://0xdata.atlassian.net/browse/PUBDEV-428)
- The quote stripper for column names should report when the stripped chars are not the expected quotes [(￼PUBDEV-424)](https://0xdata.atlassian.net/browse/PUBDEV-424)
- import directory with large files,then Frames..really slow and disk grinds. Files are unparsed. Shouldn't be grinding [(PUBDEV-98)](https://0xdata.atlassian.net/browse/PUBDEV-98)
- NodePersistentStorage gets wiped out when hadoop cluster is restarted [(HEXDEV-185)](https://0xdata.atlassian.net/browse/HEXDEV-185)
- h2o.exec won't be supported [(github)](https://github.com/h2oai/h2o-dev/commit/81f685e5abb990d7f7669b137cfb07d7b01ea471)
- fixed import issue [(github)](https://github.com/h2oai/h2o-dev/commit/addf5b85b91b77366bca0a8c900ca2d308f29a09)
- fixed init param [(github)](https://github.com/h2oai/h2o-dev/commit/d459d1a7fb405f8a1f7b466caae99281feae370c)
- fix repeat as.factor NPE [(github)](https://github.com/h2oai/h2o-dev/commit/49fb24417ecfe26975fbff14bef084da50a034c7)
- startH2O set to False in init [(github)](https://github.com/h2oai/h2o-dev/commit/53ca9baf1bd70cd04b2ad03243eb9c7053300c52)
- hang on glm job removal [(PUBDEV-726)](https://0xdata.atlassian.net/browse/PUBDEV-726)
- Flow - changed column types need to be reflected in parsed data [(HEXDEV-189)](https://0xdata.atlassian.net/browse/HEXDEV-189)
- water.DException$DistributedException while running kmeans in multinode cluster [(PUBDEV-691)](https://0xdata.atlassian.net/browse/PUBDEV-691)
- Frame inspection prior to file parsing, corrupts parsing [(PUBDEV-425)](https://0xdata.atlassian.net/browse/PUBDEV-425)






#####Web UI

- Flow, DL: Need better fail message if "Autoencoder" and "use_all_factor_levels" are both selected [(PUBDEV-724)](https://0xdata.atlassian.net/browse/PUBDEV-724)
- When select AUTO while building a gbm model get ERROR FETCHING INITIAL MODEL BUILDER STATE [(PUBDEV-595)](https://0xdata.atlassian.net/browse/PUBDEV-595)
- Flow : Build h2o-dev-0.1.17.1009 : Building GLM model gives java.lang.ArrayIndexOutOfBoundsException: [(￼PUBDEV-205](https://0xdata.atlassian.net/browse/PUBDEV-205) [(￼github)](https://github.com/h2oai/h2o-dev/commit/fe3cdad806750f6add0fc4c03bee9e66d61c59fa)
- Flow:Summary on flow broken for a long time [(PUBDEV-785)](https://0xdata.atlassian.net/browse/PUBDEV-785)

---

### Serre (0.2.1.1) - 3/18/15

####New Features


#####Algorithms
- Naive Bayes in H2O-dev [(PUBDEV-158)](https://0xdata.atlassian.net/browse/PUBDEV-158)
- GLM model output, details from R [(HEXDEV-94)](https://0xdata.atlassian.net/browse/HEXDEV-94)
- Run GLM Regression from Flow (including LBFGS) [(HEXDEV-110)](https://0xdata.atlassian.net/browse/HEXDEV-110)
- PCA [(PUBDEV-157)](https://0xdata.atlassian.net/browse/PUBDEV-157)
- Port Random Forest to h2o-dev [(PUBDEV-455)](https://0xdata.atlassian.net/browse/PUBDEV-455)
- Enable DRF model output [(github)](https://github.com/h2oai/h2o-flow/commit/44ee1bf98dd69f33251a7a959b1000cc7f290427)
- Add DRF to Flow (Model Output) [(PUBDEV-533)](https://0xdata.atlassian.net/browse/PUBDEV-533)
- Grid for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/ce96d2859aa86e4df393a13e00fbb7fcf603c166)
- Run Deep Learning Regression from Flow [(HEXDEV-109)](https://0xdata.atlassian.net/browse/HEXDEV-109)

#####Python
- Add Python wrapper for DRF [(PUBDEV-534)](https://0xdata.atlassian.net/browse/PUBDEV-534)


#####R
- Add R wrapper for DRF [(PUBDEV-530)](https://0xdata.atlassian.net/browse/PUBDEV-530)


#####System
- Include uploadFile [(PUBDEV-299)](https://0xdata.atlassian.net/browse/PUBDEV-299) [(github)](https://github.com/h2oai/h2o-flow/commit/3f8fb91cf6d81aefdb0ad6deee801084e0cf864f)
- Added -flow_dir to hadoop driver [(github)](https://github.com/h2oai/h2o-dev/commit/9883b4d98ae0056e88db449ce1ebd20394d191ac)



#####Web UI

- Add Flow packs [(HEXDEV-190)](https://0xdata.atlassian.net/browse/HEXDEV-190) [(PUBDEV-247)](https://0xdata.atlassian.net/browse/PUBDEV-247)
- Integrate H2O Help inside Help panel [(PUBDEV-108)](https://0xdata.atlassian.net/browse/PUBDEV-108) [(github)](https://github.com/h2oai/h2o-flow/commit/62e3c06e91bc0576e15516381bb59f31dbdf38ca)
- Add quick toggle button to show/hide the sidebar [(github)](https://github.com/h2oai/h2o-flow/commit/b5fb2b54a04850c9b24bb0eb03769cb519039de6)
- Add New, Open toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/b6efd33c9c8c2f5fe73e9ba83c1441d768ec47f7)
- Auto-refresh data preview when parse setup input parameters are changed [(PUBDEV-532)](https://0xdata.atlassian.net/browse/PUBDEV-532)
- Flow: Add playbar with Run, Continue, Pause, Progress controls [(HEXDEV-192)](https://0xdata.atlassian.net/browse/HEXDEV-192)
- You can now stop/cancel a running flow 


####Enhancements


#####Algorithms

- Display GLM coefficients only if available [(PUBDEV-466)](https://0xdata.atlassian.net/browse/PUBDEV-466)
- Add random chance line to RoC chart [(HEXDEV-168)](https://0xdata.atlassian.net/browse/HEXDEV-168)
- Speed up DLSpiral test. Ignore Neurons test (MatVec) [(github)](https://github.com/h2oai/h2o-dev/commit/822862aa29fb63e52703ce91794a64e49bb96aed)
- Use getRNG for Dropout [(github)](https://github.com/h2oai/h2o-dev/commit/94a5b4e46a4501e85fb4889e5c8b196c46f74525)
- PUBDEV-598: Add tests for determinism of RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/e77c3ead2151a1202ec0b9c467641bc1c787e122)
- PUBDEV-598: Implement Chi-Square test for RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/690dd333c6bf51ff4e223cd15ef9dab004ed8904)
- Add DL model output toString() [(github)](https://github.com/h2oai/h2o-dev/commit/d206bb5b9996e87e8c0058dd8f1d7580d1ea0bb1)
- Add LogLoss to MultiNomial ModelMetrics [(PUBDEV-580)](https://0xdata.atlassian.net/browse/PUBDEV-580)
- Print number of categorical levels once we hit >1000 input neurons. [(github)](https://github.com/h2oai/h2o-dev/commit/ccf645af908d4964db3bc36a98c4ff9868838dc6)
- Updated the loss behavior for GBM. When loss is set to AUTO, if the response is an integer with 2 levels, then bernoullli (rather than gaussian) behavior is chosen. As a result, the `do_classification` flag is no longer necessary in Flow, since the loss completely specifies the desired behavior, and R users no longer to use `as.factor()` in their response to get the desired bernoulli behavior. The `score_each_iteration` flag has been removed as well. [(github)](https://github.com/h2oai/h2o-dev/commit/cc971e00869197625fefec894ab705c79db05fbb)
- Fully remove `_convert_to_enum` in all algos [(github)](https://github.com/h2oai/h2o-dev/commit/7fdf5d98c1f7caf88a3a928a28b2f86b06c5b2eb)
- Port MissingValueInserter EndPoint to h2o-dev. [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)





#####API 
- Display point layer for tree vs mse plots in GBM output [(PUBDEV-504)](https://0xdata.atlassian.net/browse/PUBDEV-504)
- Rename API inputs/outputs [(github)](https://github.com/h2oai/h2o-flow/commit/c7fc17afd3ff0a176e80d9d07d71c0bdd8f165eb)
- Rename Inf to Infinity [(github)](https://github.com/h2oai/h2o-flow/commit/ef5f5997d044dac9ab676b65174f09aa8785cfb6)


#####Python
- added H2OFrame.setNames(), H2OFrame.cbind(), H2OVec.cbind(), h2o.cbind(), and pyunit_cbind.py [(github)](https://github.com/h2oai/h2o-dev/commit/84a3ea920f2ea9ee76985f7ccadb1e9d3f935025)
- Make H2OVec.levels() return the levels [(github)](https://github.com/h2oai/h2o-dev/commit/ab07275a55930b574407d8c4ea8e2b29cd6acd77)
- H2OFrame.dim(), H2OFrame.append(), H2OVec.setName(), H2OVec.isna() additions. demo pyunit addition [(github)](https://github.com/h2oai/h2o-dev/commit/41e6668ca05c59e614e54477a6082345366c75c8)


#####System

- Customize H2O web UI port [(PUBDEV-483)](https://0xdata.atlassian.net/browse/PUBDEV-483)
- Make parse setup interactive [(PUBDEV-532)](https://0xdata.atlassian.net/browse/PUBDEV-532)
- Added --verbose [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)
- Adds some H2OParseExceptions. Removes all H2O.fail in parse (no parse issues should cause a fail)[(github)](https://github.com/h2oai/h2o-dev/commit/687b674d1dfb37f13542d15d1f04fe1b7c181f71)
- Allows parse to specify check_headers=HAS_HEADERS, but not provide column names [(github)](https://github.com/h2oai/h2o-dev/commit/ba48c0af1253d4bd6b05024991241fc6f7f8532a)
- Port MissingValueInserter EndPoint to h2o-dev [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)



#####Web UI 
- Add 'Clear cell' and 'Run all cells' toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/802b3a31ed8171a43cd1e566e5f77ba7fbf33549)
- Add 'Clear cell' and 'Clear all cells' commands [(PUBDEV-493)](https://0xdata.atlassian.net/browse/PUBDEV-493) [(github)](https://github.com/h2oai/h2o-flow/commit/2ecbe04325c865d0f5d8b2cb753ca15036ea2321)
- 'Run' button selects next cell after running
- ModelMetrics by model category: Clustering [(PUBDEV-416)](https://0xdata.atlassian.net/browse/PUBDEV-416)
- ModelMetrics by model category: Regression [(PUBDEV-415)](https://0xdata.atlassian.net/browse/PUBDEV-415)
- ModelMetrics by model category: Multinomial [(PUBDEV-414)](https://0xdata.atlassian.net/browse/PUBDEV-414)
- ModelMetrics by model category: Binomial [(PUBDEV-413)](https://0xdata.atlassian.net/browse/PUBDEV-413)
- Add ability to select and delete multiple models [(github)](https://github.com/h2oai/h2o-flow/commit/8a9d033deba68292347c1e027b461a4c9ba7f1e5)
- Add ability to select and delete multiple frames [(github)](https://github.com/h2oai/h2o-flow/commit/6d5455b041f5af6b6213694ee1aae8d4e4d57d2b)
- Flows now stop running when an error occurs
- Print full number of mismatches during POJO comparison check. [(github)](https://github.com/h2oai/h2o-dev/commit/e8b599b59f2117083d2f7979cd1a0ca957a41605)
- Make Grid multi-node safe [(github)](https://github.com/h2oai/h2o-dev/commit/915cf0bd4fa589c6d819ba1eba85811e30f87399)
- Beautify the vertical axis labels for Flow charts/visualization (more) [(PUBDEV-329)](https://0xdata.atlassian.net/browse/PUBDEV-329)

####Bug Fixes

#####Algorithms

- GBM only populates either MSE_train or MSE_valid but displays both [(PUBDEV-350)](https://0xdata.atlassian.net/browse/PUBDEV-350)
- GBM: train error increases after hitting zero on prostate dataset [(PUBDEV-513)](https://0xdata.atlassian.net/browse/PUBDEV-513)
- GBM : Variable importance displays 0's for response param => should not display response in table at all [(PUBDEV-430)](https://0xdata.atlassian.net/browse/PUBDEV-430) 
- GLM : R/Flow ==> Build GLM Model hangs at 4% [(PUBDEV-456)](https://0xdata.atlassian.net/browse/PUBDEV-456)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(HEXDEV-179)](https://0xdata.atlassian.net/browse/HEXDEV-179)
- Flow: GLM - 'model.output.coefficients_magnitude.name' not found, so can't view model [(PUBDEV-466)](https://0xdata.atlassian.net/browse/PUBDEV-466)
- GBM predict fails without response column [(PUBDEV-478)](https://0xdata.atlassian.net/browse/PUBDEV-478)
- GBM: When validation set is provided, gbm should report both mse_valid and mse_train [(PUBDEV-499)](https://0xdata.atlassian.net/browse/PUBDEV-499)
- PCA Assertion Error during Model Metrics [(PUBDEV-548)](https://0xdata.atlassian.net/browse/PUBDEV-548) [(github)](https://github.com/h2oai/h2o-dev/commit/69690db57ed9951a57df83b2ce30be30a49ca507)
- KMeans: Size of clusters in Model Output is different from the labels generated on the training set [(PUBDEV-542)](https://0xdata.atlassian.net/browse/PUBDEV-542) [(github)](https://github.com/h2oai/h2o-dev/commit/6f8a857c8a060af0d2434cda91469ef8c23c86ae)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(HEXDEV-194)](https://0xdata.atlassian.net/browse/HEXDEV-194)
- PUBDEV-580: Fix some numerical edge cases [(github)](https://github.com/h2oai/h2o-dev/commit/4affd9baa005c08d6b1669e462ec7bfb4de5ec69)
- Fix two missing float -> double conversion changes in tree scoring. [(github)](https://github.com/h2oai/h2o-dev/commit/b2cc99822db9b59766f3293e4dbbeeea547cd81e)
- Flow: HIDDEN_DROPOUT_RATIOS for DL does not show default value [(PUBDEV-285)](https://0xdata.atlassian.net/browse/PUBDEV-285)
- Old GLM Parameters Missing [(PUBDEV-431)](https://0xdata.atlassian.net/browse/PUBDEV-431)
- GLM: R/Flow ==> Build GLM Model hangs at 4% [(PUBDEV-456)](https://0xdata.atlassian.net/browse/PUBDEV-456)

 



#####API
- SplitFrame on String column produce C0LChunk instead of CStrChunk [(PUBDEV-468)](https://0xdata.atlassian.net/browse/PUBDEV-468)
-  Error in node$h2o$node : $ operator is invalid for atomic vectors [(PUBDEV-348)](https://0xdata.atlassian.net/browse/PUBDEV-348)
-  Response from /ModelBuilders don't conform to standard error json shape when there are errors [(HEXDEV-121)](https://0xdata.atlassian.net/browse/HEXDEV-121) [(github)](https://github.com/h2oai/h2o-dev/commit/dadf385b3e3b2f68afe88096ecfd51e5bc9e01cb)

#####Python
- fix python syntax error [(github)](https://github.com/h2oai/h2o-dev/commit/a3c62f099088ac2206b83275ca096d4952f76e28)
- Fixes handling of None in python for a returned na_string. [(github)](https://github.com/h2oai/h2o-dev/commit/58c1af54b37909b8e9d06d23ed41fce4943eceb4)



#####R
- R : Inconsistency - Train set name with and without quotes work but Validation set name with quotes does not work [(PUBDEV-491)](https://0xdata.atlassian.net/browse/PUBDEV-491)
- h2o.confusionmatrices does not work [(PUBDEV-547)](https://0xdata.atlassian.net/browse/PUBDEV-547)
- How do i convert an enum column back to integer/double from R? [(PUBDEV-546)](https://0xdata.atlassian.net/browse/PUBDEV-546)
- Summary in R is faulty [(PUBDEV-539)](https://0xdata.atlassian.net/browse/PUBDEV-539)
- R: as.h2o should preserve R data types [(PUBDEV-578)](https://0xdata.atlassian.net/browse/PUBDEV-578)
- NPE in GBM Prediction with Sliced Test Data [(HEXDEV-207)](https://0xdata.atlassian.net/browse/HEXDEV-207) [(github)](https://github.com/h2oai/h2o-dev/commit/e605ab109488c7630223320fdd8bad486492050a)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(HEXDEV-179)](https://0xdata.atlassian.net/browse/HEXDEV-179)
- Custom Functions don't work in apply() in R [(PUBDEV-436)](https://0xdata.atlassian.net/browse/PUBDEV-436)
- got water.DException$DistributedException and then got java.lang.RuntimeException: Categorical renumber task [(HEXDEV-195)](https://0xdata.atlassian.net/browse/HEXDEV-195)
- H2O-R: as.h2o parses column name as one of the row entries [(PUBDEV-591)](https://0xdata.atlassian.net/browse/PUBDEV-591)
- R-H2O Managing Memory in a loop [(PUB-1125)](https://0xdata.atlassian.net/browse/PUB-1125)
- h2o.confusionMatrices for multinomial does not work [(PUBDEV-577)](https://0xdata.atlassian.net/browse/PUBDEV-577)
- H2O-R not showing meaningful error msg 





#####System
- Flow: When balance class = F then flow should not show max_after_balance_size = 5 in the parameter listing [(PUBDEV-503)](https://0xdata.atlassian.net/browse/PUBDEV-503)
- 3 jvms, doing ModelMetrics on prostate, class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <=  122 [(PUBDEV-495)](https://0xdata.atlassian.net/browse/PUBDEV-495)
- Not able to start h2o on hadoop [(PUBDEV-487)](https://0xdata.atlassian.net/browse/PUBDEV-487)
- one row (one col) dataset seems to get assertion error in parse setup request [(PUBDEV-96)](https://0xdata.atlassian.net/browse/PUBDEV-96)
- Parse : Import file (move.com) => Parse => First row contains column names => column names not selected [(HEXDEV-171)](https://0xdata.atlassian.net/browse/HEXDEV-171) [(github)](https://github.com/h2oai/h2o-dev/commit/6f6d7023f9f2bafcb5461f46cf2825f233779f4a)
- The NY0 parse rule, in summary. Doesn't look like it's counting the 0's as NAs like h2o [(PUBDEV-154)](https://0xdata.atlassian.net/browse/PUBDEV-154)
- 0 / Y / N parsing [(PUBDEV-229)](https://0xdata.atlassian.net/browse/PUBDEV-229)
- NodePersistentStorage gets wiped out when laptop is restarted. [(HEXDEV-167)](https://0xdata.atlassian.net/browse/HEXDEV-167)
- Building a model and making a prediction accepts invalid frame types [(PUBDEV-83)](https://0xdata.atlassian.net/browse/PUBDEV-83)
- Flow : Import file 15M rows 2.2 Cols => Parse => Error fetching job on UI =>Console : ERROR: Job was not successful Exiting with nonzero exit status [(HEXDEV-55)](https://0xdata.atlassian.net/browse/HEXDEV-55)
- Flow : Build GLM Model => Family tweedy => class hex.glm.LSMSolver$ADMMSolver$NonSPDMatrixException', with msg 'Matrix is not SPD, can't solve without regularization [(PUBDEV-211)](https://0xdata.atlassian.net/browse/PUBDEV-211)
- Flow : Import File : File doesn't exist on all the hdfs nodes => Fails without valid message [(PUBDEV-313)](https://0xdata.atlassian.net/browse/PUBDEV-313)
- Check reproducibility on multi-node vs single-node [(PUBDEV-557)](https://0xdata.atlassian.net/browse/PUBDEV-557)
- Parse : After parsing Chicago crime dataset => Not able to build models or Get frames [(PUBDEV-576)](https://0xdata.atlassian.net/browse/PUBDEV-576)
 




#####Web UI
- Flow : Build Model => Parameters => shows meta text for some params [(PUBDEV-505)](https://0xdata.atlassian.net/browse/PUBDEV-505)
- Flow: K-Means - "None" option should not appear in "Init" parameters [(PUBDEV-459)](https://0xdata.atlassian.net/browse/PUBDEV-459)
- Flow: PCA - "None" option appears twice in "Transform" list [(HEXDEV-186)](https://0xdata.atlassian.net/browse/HEXDEV-186)
- GBM Model : Params in flow show two times [(PUBDEV-440)](https://0xdata.atlassian.net/browse/PUBDEV-440)
- Flow multinomial confusion matrix visualization [(HEXDEV-204)](https://0xdata.atlassian.net/browse/HEXDEV-204)
- Flow: It would be good if flow can report the actual distribution, instead of just reporting "Auto" in the model parameter listing [(PUBDEV-509)](https://0xdata.atlassian.net/browse/PUBDEV-509)
- Unimplemented algos should be taken out from drop down of build model [(PUBDEV-511)](https://0xdata.atlassian.net/browse/PUBDEV-511)
- [MapR] unable to give hdfs file name from Flow [(PUBDEV-409)](https://0xdata.atlassian.net/browse/PUBDEV-409)





---

###Selberg (0.2.0.1) - 3/6/15
####New Features


#####Algorithms
- Naive Bayes in H2O-dev [(PUBDEV-158)](https://0xdata.atlassian.net/browse/PUBDEV-158)
- GLM model output, details from R [(HEXDEV-94)](https://0xdata.atlassian.net/browse/HEXDEV-94)
- Run GLM Regression from Flow (including LBFGS) [(HEXDEV-110)](https://0xdata.atlassian.net/browse/HEXDEV-110)
- PCA [(PUBDEV-157)](https://0xdata.atlassian.net/browse/PUBDEV-157)
- Port Random Forest to h2o-dev [(PUBDEV-455)](https://0xdata.atlassian.net/browse/PUBDEV-455)
- Enable DRF model output [(github)](https://github.com/h2oai/h2o-flow/commit/44ee1bf98dd69f33251a7a959b1000cc7f290427)
- Add DRF to Flow (Model Output) [(PUBDEV-533)](https://0xdata.atlassian.net/browse/PUBDEV-533)
- Grid for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/ce96d2859aa86e4df393a13e00fbb7fcf603c166)
- Run Deep Learning Regression from Flow [(HEXDEV-109)](https://0xdata.atlassian.net/browse/HEXDEV-109)

#####Python
- Add Python wrapper for DRF [(PUBDEV-534)](https://0xdata.atlassian.net/browse/PUBDEV-534)


#####R
- Add R wrapper for DRF [(PUBDEV-530)](https://0xdata.atlassian.net/browse/PUBDEV-530)



#####System
- Include uploadFile [(PUBDEV-299)](https://0xdata.atlassian.net/browse/PUBDEV-299) [(github)](https://github.com/h2oai/h2o-flow/commit/3f8fb91cf6d81aefdb0ad6deee801084e0cf864f)
- Added -flow_dir to hadoop driver [(github)](https://github.com/h2oai/h2o-dev/commit/9883b4d98ae0056e88db449ce1ebd20394d191ac)



#####Web UI

- Add Flow packs [(HEXDEV-190)](https://0xdata.atlassian.net/browse/HEXDEV-190) [(PUBDEV-247)](https://0xdata.atlassian.net/browse/PUBDEV-247)
- Integrate H2O Help inside Help panel [(PUBDEV-108)](https://0xdata.atlassian.net/browse/PUBDEV-108) [(github)](https://github.com/h2oai/h2o-flow/commit/62e3c06e91bc0576e15516381bb59f31dbdf38ca)
- Add quick toggle button to show/hide the sidebar [(github)](https://github.com/h2oai/h2o-flow/commit/b5fb2b54a04850c9b24bb0eb03769cb519039de6)
- Add New, Open toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/b6efd33c9c8c2f5fe73e9ba83c1441d768ec47f7)
- Auto-refresh data preview when parse setup input parameters are changed [(PUBDEV-532)](https://0xdata.atlassian.net/browse/PUBDEV-532)
-Flow: Add playbar with Run, Continue, Pause, Progress controls [(HEXDEV-192)](https://0xdata.atlassian.net/browse/HEXDEV-192)
- You can now stop/cancel a running flow 


####Enhancements

The following changes are improvements to existing features (which includes changed default values):

#####Algorithms

- Display GLM coefficients only if available [(PUBDEV-466)](https://0xdata.atlassian.net/browse/PUBDEV-466)
- Add random chance line to RoC chart [(HEXDEV-168)](https://0xdata.atlassian.net/browse/HEXDEV-168)
- Allow validation dataset for AutoEncoder [(PUDEV-581)](https://0xdata.atlassian.net/browse/PUBDEV-581)
- Speed up DLSpiral test. Ignore Neurons test (MatVec) [(github)](https://github.com/h2oai/h2o-dev/commit/822862aa29fb63e52703ce91794a64e49bb96aed)
- Use getRNG for Dropout [(github)](https://github.com/h2oai/h2o-dev/commit/94a5b4e46a4501e85fb4889e5c8b196c46f74525)
- PUBDEV-598: Add tests for determinism of RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/e77c3ead2151a1202ec0b9c467641bc1c787e122)
- PUBDEV-598: Implement Chi-Square test for RNGs [(github)](https://github.com/h2oai/h2o-dev/commit/690dd333c6bf51ff4e223cd15ef9dab004ed8904)
- PUBDEV-580: Add log loss to binomial and multinomial model metric [(github)](https://github.com/h2oai/h2o-dev/commit/8982a0a1ba575bd5ca6ca3e854382e03146743cd)
- Add DL model output toString() [(github)](https://github.com/h2oai/h2o-dev/commit/d206bb5b9996e87e8c0058dd8f1d7580d1ea0bb1)
- Add LogLoss to MultiNomial ModelMetrics [(PUBDEV-580)](https://0xdata.atlassian.net/browse/PUBDEV-580)
- Port MissingValueInserter EndPoint to h2o-dev [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)
- Print number of categorical levels once we hit >1000 input neurons. [(github)](https://github.com/h2oai/h2o-dev/commit/ccf645af908d4964db3bc36a98c4ff9868838dc6)
- Updated the loss behavior for GBM. When loss is set to AUTO, if the response is an integer with 2 levels, then bernoullli (rather than gaussian) behavior is chosen. As a result, the `do_classification` flag is no longer necessary in Flow, since the loss completely specifies the desired behavior, and R users no longer to use `as.factor()` in their response to get the desired bernoulli behavior. The `score_each_iteration` flag has been removed as well. [(github)](https://github.com/h2oai/h2o-dev/commit/cc971e00869197625fefec894ab705c79db05fbb)
- Fully remove `_convert_to_enum` in all algos [(github)](https://github.com/h2oai/h2o-dev/commit/7fdf5d98c1f7caf88a3a928a28b2f86b06c5b2eb)
- Add DL POJO scoring [(PUBDEV-585)](https://0xdata.atlassian.net/browse/PUBDEV-585)





#####API 
- Display point layer for tree vs mse plots in GBM output [(PUBDEV-504)](https://0xdata.atlassian.net/browse/PUBDEV-504)
- Rename API inputs/outputs [(github)](https://github.com/h2oai/h2o-flow/commit/c7fc17afd3ff0a176e80d9d07d71c0bdd8f165eb)
- Rename Inf to Infinity [(github)](https://github.com/h2oai/h2o-flow/commit/ef5f5997d044dac9ab676b65174f09aa8785cfb6)


#####Python
- added H2OFrame.setNames(), H2OFrame.cbind(), H2OVec.cbind(), h2o.cbind(), and pyunit_cbind.py [(github)](https://github.com/h2oai/h2o-dev/commit/84a3ea920f2ea9ee76985f7ccadb1e9d3f935025)
- Make H2OVec.levels() return the levels [(github)](https://github.com/h2oai/h2o-dev/commit/ab07275a55930b574407d8c4ea8e2b29cd6acd77)
- H2OFrame.dim(), H2OFrame.append(), H2OVec.setName(), H2OVec.isna() additions. demo pyunit addition [(github)](https://github.com/h2oai/h2o-dev/commit/41e6668ca05c59e614e54477a6082345366c75c8)


#####R
- PUBDEV-578, PUBDEV-541, PUBDEV-566.
	-R client now sends the data frame column names and data types to ParseSetup.
	-R client can get column names from a parsed frame or a list.
	-Respects client request for column data types [(github)](https://github.com/h2oai/h2o-dev/commit/ba063be25d3fbb658b016ff514083284e2d95d78)

#####System

- Customize H2O web UI port [(PUBDEV-483)](https://0xdata.atlassian.net/browse/PUBDEV-483)
- Make parse setup interactive [(PUBDEV-532)](https://0xdata.atlassian.net/browse/PUBDEV-532)
- Added --verbose [(github)](https://github.com/h2oai/h2o-dev/commit/5e772f8314a340666e4e80b3480b2105ceb91251)
- Adds some H2OParseExceptions. Removes all H2O.fail in parse (no parse issues should cause a fail)[(github)](https://github.com/h2oai/h2o-dev/commit/687b674d1dfb37f13542d15d1f04fe1b7c181f71)
- Allows parse to specify check_headers=HAS_HEADERS, but not provide column names [(github)](https://github.com/h2oai/h2o-dev/commit/ba48c0af1253d4bd6b05024991241fc6f7f8532a)
- Port MissingValueInserter EndPoint to h2o-dev [(PUBDEV-465)](https://0xdata.atlassian.net/browse/PUBDEV-465)



#####Web UI 
- Add 'Clear cell' and 'Run all cells' toolbar buttons [(github)](https://github.com/h2oai/h2o-flow/commit/802b3a31ed8171a43cd1e566e5f77ba7fbf33549)
- Add 'Clear cell' and 'Clear all cells' commands [(PUBDEV-493)](https://0xdata.atlassian.net/browse/PUBDEV-493) [(github)](https://github.com/h2oai/h2o-flow/commit/2ecbe04325c865d0f5d8b2cb753ca15036ea2321)
- 'Run' button selects next cell after running
- ModelMetrics by model category: Clustering [(PUBDEV-416)](https://0xdata.atlassian.net/browse/PUBDEV-416)
- ModelMetrics by model category: Regression [(PUBDEV-415)](https://0xdata.atlassian.net/browse/PUBDEV-415)
- ModelMetrics by model category: Multinomial [(PUBDEV-414)](https://0xdata.atlassian.net/browse/PUBDEV-414)
- ModelMetrics by model category: Binomial [(PUBDEV-413)](https://0xdata.atlassian.net/browse/PUBDEV-413)
- Add ability to select and delete multiple models [(github)](https://github.com/h2oai/h2o-flow/commit/8a9d033deba68292347c1e027b461a4c9ba7f1e5)
- Add ability to select and delete multiple frames [(github)](https://github.com/h2oai/h2o-flow/commit/6d5455b041f5af6b6213694ee1aae8d4e4d57d2b)
- Flows now stop running when an error occurs
- Print full number of mismatches during POJO comparison check. [(github)](https://github.com/h2oai/h2o-dev/commit/e8b599b59f2117083d2f7979cd1a0ca957a41605)
- Make Grid multi-node safe [(github)](https://github.com/h2oai/h2o-dev/commit/915cf0bd4fa589c6d819ba1eba85811e30f87399)
- Beautify the vertical axis labels for Flow charts/visualization (more) [(PUBDEV-329)](https://0xdata.atlassian.net/browse/PUBDEV-329)

####Bug Fixes
The following changes are to resolve incorrect software behavior: 

#####Algorithms

- GBM only populates either MSE_train or MSE_valid but displays both [(PUBDEV-350)](https://0xdata.atlassian.net/browse/PUBDEV-350)
- GBM: train error increases after hitting zero on prostate dataset [(PUBDEV-513)](https://0xdata.atlassian.net/browse/PUBDEV-513)
- GBM : Variable importance displays 0's for response param => should not display response in table at all [(PUBDEV-430)](https://0xdata.atlassian.net/browse/PUBDEV-430) 
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(HEXDEV-194)](https://0xdata.atlassian.net/browse/HEXDEV-194)
- GLM : R/Flow ==> Build GLM Model hangs at 4% [(PUBDEV-456)](https://0xdata.atlassian.net/browse/PUBDEV-456)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(HEXDEV-179)](https://0xdata.atlassian.net/browse/HEXDEV-179)
- Flow: GLM - 'model.output.coefficients_magnitude.name' not found, so can't view model [(PUBDEV-466)](https://0xdata.atlassian.net/browse/PUBDEV-466)
- GBM predict fails without response column [(PUBDEV-478)](https://0xdata.atlassian.net/browse/PUBDEV-478)
- GBM: When validation set is provided, gbm should report both mse_valid and mse_train [(PUBDEV-499)](https://0xdata.atlassian.net/browse/PUBDEV-499)
- PCA Assertion Error during Model Metrics [(PUBDEV-548)](https://0xdata.atlassian.net/browse/PUBDEV-548) [(github)](https://github.com/h2oai/h2o-dev/commit/69690db57ed9951a57df83b2ce30be30a49ca507)
- KMeans: Size of clusters in Model Output is different from the labels generated on the training set [(PUBDEV-542)](https://0xdata.atlassian.net/browse/PUBDEV-542) [(github)](https://github.com/h2oai/h2o-dev/commit/6f8a857c8a060af0d2434cda91469ef8c23c86ae)
- Inconsistency in GBM results:Gives different results even when run with the same set of params [(HEXDEV-194)](https://0xdata.atlassian.net/browse/HEXDEV-194)
- divide by zero in modelmetrics for deep learning [(PUBDEV-568)](https://0xdata.atlassian.net/browse/PUBDEV-568)
- AUC reported on training data is 0, but should be 1 [(HEXDEV-223)](https://0xdata.atlassian.net/browse/HEXDEV-223) [(github)](https://github.com/h2oai/h2o-dev/commit/312558524749a0b28bf22ffd8c34ebcd6996b350)
- GBM: reports 0th tree mse value for the validation set, different than the train set ,When only train sets is provided [(PUDEV-561)](https://0xdata.atlassian.net/browse/PUBDEV-561)
- PUBDEV-580: Fix some numerical edge cases [(github)](https://github.com/h2oai/h2o-dev/commit/4affd9baa005c08d6b1669e462ec7bfb4de5ec69)
- Fix two missing float -> double conversion changes in tree scoring. [(github)](https://github.com/h2oai/h2o-dev/commit/b2cc99822db9b59766f3293e4dbbeeea547cd81e)
- Problems during Train/Test adaptation between Enum/Numeric [(HEXDEV-229)](https://0xdata.atlassian.net/browse/HEXDEV-229)
- DRF/GBM balance_classes=True throws unimplemented exception [(HEXDEV-226)](https://0xdata.atlassian.net/browse/HEXDEV-226)
- Flow: HIDDEN_DROPOUT_RATIOS for DL does not show default value [(PUBDEV-285)](https://0xdata.atlassian.net/browse/PUBDEV-285)
- Old GLM Parameters Missing [(PUBDEV-431)](https://0xdata.atlassian.net/browse/PUBDEV-431)
- GLM: R/Flow ==> Build GLM Model hangs at 4% [(PUBDEV-456)](https://0xdata.atlassian.net/browse/PUBDEV-456)
- GBM: Initial mse in bernoulli seems to be off [(PUBDEV-515)](https://0xdata.atlassian.net/browse/PUBDEV-515) 
 



#####API
- SplitFrame on String column produce C0LChunk instead of CStrChunk [(PUBDEV-468)](https://0xdata.atlassian.net/browse/PUBDEV-468)
-  Error in node$h2o$node : $ operator is invalid for atomic vectors [(PUBDEV-348)](https://0xdata.atlassian.net/browse/PUBDEV-348)
-  Response from /ModelBuilders don't conform to standard error json shape when there are errors [(HEXDEV-121)](https://0xdata.atlassian.net/browse/HEXDEV-121)

#####Python
- fix python syntax error [(github)](https://github.com/h2oai/h2o-dev/commit/a3c62f099088ac2206b83275ca096d4952f76e28)
- Fixes handling of None in python for a returned na_string. [(github)](https://github.com/h2oai/h2o-dev/commit/58c1af54b37909b8e9d06d23ed41fce4943eceb4)


#####R
- R : Inconsistency - Train set name with and without quotes work but Validation set name with quotes does not work [(PUBDEV-491)](https://0xdata.atlassian.net/browse/PUBDEV-491)
- h2o.confusionmatrices does not work [(PUBDEV-547)](https://0xdata.atlassian.net/browse/PUBDEV-547)
- How do i convert an enum column back to integer/double from R? [(PUBDEV-546)](https://0xdata.atlassian.net/browse/PUBDEV-546)
- Summary in R is faulty [(PUBDEV-539)](https://0xdata.atlassian.net/browse/PUBDEV-539)
- Custom Functions don't work in apply() in R [(PUBDEV-436)](https://0xdata.atlassian.net/browse/PUBDEV-436)
- R: as.h2o should preserve R data types [(PUBDEV-578)](https://0xdata.atlassian.net/browse/PUBDEV-578)
- as.h2o loses track of headers [(PUBDEV-541)](https://0xdata.atlassian.net/browse/PUBDEV-541)
- NPE in GBM Prediction with Sliced Test Data [(HEXDEV-207)](https://0xdata.atlassian.net/browse/HEXDEV-207) [(github)](https://github.com/h2oai/h2o-dev/commit/e605ab109488c7630223320fdd8bad486492050a)
- Import file from R hangs at 75% for 15M Rows/2.2 K Columns [(HEXDEV-179)](https://0xdata.atlassian.net/browse/HEXDEV-179)
- Custom Functions don't work in apply() in R [(PUBDEV-436)](https://0xdata.atlassian.net/browse/PUBDEV-436)
- got water.DException$DistributedException and then got java.lang.RuntimeException: Categorical renumber task [(HEXDEV-195)](https://0xdata.atlassian.net/browse/HEXDEV-195)
- h2o.confusionMatrices for multinomial does not work [(PUBDEV-577)](https://0xdata.atlassian.net/browse/PUBDEV-577)
- R: h2o.confusionMatrix should handle both models and model metric objects [(PUBDEV-590)](https://0xdata.atlassian.net/browse/PUBDEV-590)
- H2O-R: as.h2o parses column name as one of the row entries [(PUBDEV-591)](https://0xdata.atlassian.net/browse/PUBDEV-591)


#####System
- Flow: When balance class = F then flow should not show max_after_balance_size = 5 in the parameter listing [(PUBDEV-503)](https://0xdata.atlassian.net/browse/PUBDEV-503)
- 3 jvms, doing ModelMetrics on prostate, class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <=  122 [(PUBDEV-495)](https://0xdata.atlassian.net/browse/PUBDEV-495)
- Not able to start h2o on hadoop [(PUBDEV-487)](https://0xdata.atlassian.net/browse/PUBDEV-487)
- one row (one col) dataset seems to get assertion error in parse setup request [(PUBDEV-96)](https://0xdata.atlassian.net/browse/PUBDEV-96)
- Parse : Import file (move.com) => Parse => First row contains column names => column names not selected [(HEXDEV-171)](https://0xdata.atlassian.net/browse/HEXDEV-171) [(github)](https://github.com/h2oai/h2o-dev/commit/6f6d7023f9f2bafcb5461f46cf2825f233779f4a)
- The NY0 parse rule, in summary. Doesn't look like it's counting the 0's as NAs like h2o [(PUBDEV-154)](https://0xdata.atlassian.net/browse/PUBDEV-154)
- 0 / Y / N parsing [(PUBDEV-229)](https://0xdata.atlassian.net/browse/PUBDEV-229)
- NodePersistentStorage gets wiped out when laptop is restarted. [(HEXDEV-167)](https://0xdata.atlassian.net/browse/HEXDEV-167)
- Parse : Parsing random crap gives java.lang.ArrayIndexOutOfBoundsException: 13 [(PUBDEV-428)](https://0xdata.atlassian.net/browse/PUBDEV-428)
- Flow: converting a column to enum while parsing does not work [(PUBDEV-566)](https://0xdata.atlassian.net/browse/PUBDEV-566)
- Parse: Numbers completely parsed wrong [(PUBDEV-574)](https://0xdata.atlassian.net/browse/PUBDEV-574)
- NodePersistentStorage gets wiped out when hadoop cluster is restarted [(HEXDEV-185)](https://0xdata.atlassian.net/browse/HEXDEV-185)
- Parse: Fail gracefully when asked to parse a zip file with different files in it [(PUBDEV-540)](https://0xdata.atlassian.net/browse/PUBDEV-540)[(github)](https://github.com/h2oai/h2o-dev/commit/23a60d68e9d77fe07ae9d940b0ebb6636ef40ee3)
- Building a model and making a prediction accepts invalid frame types [(PUBDEV-83)](https://0xdata.atlassian.net/browse/PUBDEV-83)
- Flow : Import file 15M rows 2.2 Cols => Parse => Error fetching job on UI =>Console : ERROR: Job was not successful Exiting with nonzero exit status [(HEXDEV-55)](https://0xdata.atlassian.net/browse/HEXDEV-55)
- Flow : Build GLM Model => Family tweedy => class hex.glm.LSMSolver$ADMMSolver$NonSPDMatrixException', with msg 'Matrix is not SPD, can't solve without regularization [(PUBDEV-211)](https://0xdata.atlassian.net/browse/PUBDEV-211)
- Flow : Import File : File doesn't exist on all the hdfs nodes => Fails without valid message [(PUBDEV-313)](https://0xdata.atlassian.net/browse/PUBDEV-313)
- Check reproducibility on multi-node vs single-node [(PUBDEV-557)](https://0xdata.atlassian.net/browse/PUBDEV-557)
- Parse: After parsing Chicago crime dataset => Not able to build models or Get frames [(PUBDEV-576)](https://0xdata.atlassian.net/browse/PUBDEV-576)

#####Web UI
- Flow : Build Model => Parameters => shows meta text for some params [(PUBDEV-505)](https://0xdata.atlassian.net/browse/PUBDEV-505)
- Flow: K-Means - "None" option should not appear in "Init" parameters [(PUBDEV-459)](https://0xdata.atlassian.net/browse/PUBDEV-459)
- Flow: PCA - "None" option appears twice in "Transform" list [(HEXDEV-186)](https://0xdata.atlassian.net/browse/HEXDEV-186)
- GBM Model : Params in flow show two times [(PUBDEV-440)](https://0xdata.atlassian.net/browse/PUBDEV-440)
- Flow multinomial confusion matrix visualization [(HEXDEV-204)](https://0xdata.atlassian.net/browse/HEXDEV-204)
- Flow: It would be good if flow can report the actual distribution, instead of just reporting "Auto" in the model parameter listing [(PUBDEV-509)](https://0xdata.atlassian.net/browse/PUBDEV-509)
- Unimplemented algos should be taken out from drop down of build model [(PUBDEV-511)](https://0xdata.atlassian.net/browse/PUBDEV-511)
- [MapR] unable to give hdfs file name from Flow [(PUBDEV-409)](https://0xdata.atlassian.net/browse/PUBDEV-409)




---

###Selberg (0.2.0.1) - 3/6/15
####New Features

#####Web UI

- Flow: Delete functionality to be available for import files, jobs, models, frames [(PUBDEV-241)](https://0xdata.atlassian.net/browse/PUBDEV-241)
- Implement "Download Flow" [(PUBDEV-407)](https://0xdata.atlassian.net/browse/PUBDEV-407)
- Flow: Implement "Run All Cells" [(PUBDEV-110)](https://0xdata.atlassian.net/browse/PUBDEV-110)

#####API 
- Create python package [(PUBDEV-181)](https://0xdata.atlassian.net/browse/PUBDEV-181)
- as.h2o in Python [(HEXDEV-72)](https://0xdata.atlassian.net/browse/HEXDEV-72)

#####System
- Add a README.txt to the hadoop zip files [(github)](https://github.com/h2oai/h2o-dev/commit/5a06ba8f0cfead3e30737d336f3c389ca0775b58)
- Build a cdh5.2 version of h2o [(github)](https://github.com/h2oai/h2o-dev/commit/eb8855d103e4f3aaf9dfa8c07d40d6c848141245)

####Enhancements 

#####Web UI
- Flow: Job view should have info on start and end time [(PUBDEV-267)](https://0xdata.atlassian.net/browse/PUBDEV-267)
- Flow: Implement 'File > Open' [(PUBDEV-408)](https://0xdata.atlassian.net/browse/PUBDEV-408)
- Display IP address in ADMIN -> Cluster Status [(HEXDEV-159)](https://0xdata.atlassian.net/browse/HEXDEV-159)
- Flow: Display alternate UI for splitFrames() [(PUBDEV-399)](https://0xdata.atlassian.net/browse/PUBDEV-399)


#####Algorithms
- Added K-Means scoring [(github)](https://github.com/h2oai/h2o-dev/commit/220d2b40dc36dee6975a101e2eacb56a77861194)
- Flow: Implement model output for Deep Learning [(PUBDEV-118)](https://0xdata.atlassian.net/browse/PUBDEV-118)
- Flow: Implement model output for GLM [(PUBDEV-120)](https://0xdata.atlassian.net/browse/PUBDEV-120)
- Deep Learning model output [(HEXDEV-89, Flow)](https://0xdata.atlassian.net/browse/HEXDEV-89),[(HEXDEV-88, Python)](https://0xdata.atlassian.net/browse/HEXDEV-88),[(HEXDEV-87, R)](https://0xdata.atlassian.net/browse/HEXDEV-87)
- Run GLM Binomial from Flow (including LBFGS) [(HEXDEV-90)](https://0xdata.atlassian.net/browse/HEXDEV-90)
- Flow: Display confusion matrices for multinomial models [(PUBDEV-397)](https://0xdata.atlassian.net/browse/PUBDEV-397)
- During PCA, missing values in training data will be replaced with column mean [(github)](https://github.com/h2oai/h2o-dev/commit/166efad882162f7edc5cd8d4baa189476aa72d25)
- Update parameters for best model scan [(github)](https://github.com/h2oai/h2o-dev/commit/f183de392cb45adea7af43ffa53b095c3764602f)
- Change Quantiles to match h2o-1; both Quantiles and Rollups now have the same default percentiles [(github)](https://github.com/h2oai/h2o-dev/commit/51dc2c12a4281e3a2beeed8adfdfe4b14736fead)
- Massive cleanup and removal of old PCA, replacing with quadratically regularized PCA based on alternating minimization algorithm in GLRM [(github)](https://github.com/h2oai/h2o-dev/commit/02b7f168b2efa551a60c4bf2e95b8d506b613c2d)
- Add model run time to DL Model Output [(github)](https://github.com/h2oai/h2o-dev/commit/6730cc530b7b5376dfe6a2dd71817065e1edab7d)
- Don't gather Neurons/Weights/Biases statistics [(github)](https://github.com/h2oai/h2o-dev/commit/aa1360d1bcfad3628d23211284878d80aa5a3b21)
- Only store best model if `override_with_best_model` is enabled [(github)](https://github.com/h2oai/h2o-dev/commit/5bd1e2327a09b649f251b251ff72af9aa8f4824c)
- `beta_eps` added, passing tests changed [(github)](https://github.com/h2oai/h2o-dev/commit/5e5acb6bdb89ff966151b0bc1ae20e96577d0368)
- For GLM, default values for `max_iters` parameter were changed from 1000 to 50. 
- For quantiles, probabilities are displayed. 
- Run Deep Learning Multinomial from Flow [(HEXDEV-108)](https://0xdata.atlassian.net/browse/HEXDEV-108)



#####API
- Expose DL weights/biases to clients via REST call [(PUBDEV-344)](https://0xdata.atlassian.net/browse/PUBDEV-344)
- Flow: Implement notification bar/API [(PUBDEV-359)](https://0xdata.atlassian.net/browse/PUBDEV-116)
- Variable importance data in REST output for GLM [(PUBDEV-359)](https://0xdata.atlassian.net/browse/PUBDEV-359)
- Add extra DL parameters to R API (`average_activation, sparsity_beta, max_categorical_features, reproducible`) [(github)](https://github.com/h2oai/h2o-dev/commit/8c7b860e29f297ff42ad6f45a1f138a8c6bb6b29)
- Update GLRM API model output [(github)](https://github.com/h2oai/h2o-dev/commit/653a9906003c2bab5e65d576420c76093fc92d12) 
- h2o.anomaly missing in R [(PUBDEV-434)](https://0xdata.atlassian.net/browse/PUBDEV-434)
- No method to get enum levels [(PUBDEV-432)](https://0xdata.atlassian.net/browse/PUBDEV-432)



#####System
- Improve memory footprint with latest version of h2o-dev [(github)](https://github.com/h2oai/h2o-dev/commit/c54efaf41bc13677d5acd53a0496cca2b192baef)
- For now, let model.delete() of DL delete its best models too. This allows R code to not leak when only calling h2o.rm() on the main model. [(github)](https://github.com/h2oai/h2o-dev/commit/08b151a2bcbef8d56063b576638a6c0250379bd0)
- Bind both TCP and UDP ports before clustering [(github)](https://github.com/h2oai/h2o-dev/commit/d83c35841800b2abcc9d479fc74583d6ccdc714c)
- Round summary row#. Helps with pctiles for very small row counts. Add a test to check for getting close to the 50% percentile on small rows. [(github)](https://github.com/h2oai/h2o-dev/commit/7f4f7b159de0041894166f62d21e694dbd9c4c5d)
- Increase Max Value size in DKV to 256MB [(github)](https://github.com/h2oai/h2o-dev/commit/336b06e2a129509d424156653a2e7e4d5e972ed8)
- Flow: make parseRaw() do both import and parse in sequence [(HEXDEV-184)](https://0xdata.atlassian.net/browse/HEXDEV-184)
- Remove notion of individual job/job tracking from Flow [(PUBDEV-449)](https://0xdata.atlassian.net/browse/PUBDEV-449)
- Capability to name prediction results Frame in flow [(PUBDEV-233)](https://0xdata.atlassian.net/browse/PUBDEV-233)



####Bug Fixes

#####Algorithms

- GLM binomial prediction failing [(PUBDEV-403)](https://0xdata.atlassian.net/browse/PUBDEV-403)
- DL: Predict with auto encoder enabled gives Error processing error [(PUBDEV-433)](https://0xdata.atlassian.net/browse/PUBDEV-433)
- balance_classes in Deep Learning intermittent poor result [(PUBDEV-437)](https://0xdata.atlassian.net/browse/PUBDEV-437)
- Flow: Building GLM model fails [(PUBDEV-186)](https://0xdata.atlassian.net/browse/PUBDEV-186)
- summary returning incorrect 0.5 quantile for 5 row dataset [(PUBDEV-95)](https://0xdata.atlassian.net/browse/PUBDEV-95)
- GBM missing variable importance and balance-classes [(PUBDEV-309)](https://0xdata.atlassian.net/browse/PUBDEV-309)
- H2O Dev GBM first tree differs from H2O 1 [(PUBDEV-421)](https://0xdata.atlassian.net/browse/PUBDEV-421)
- get glm model from flow fails to find coefficient name field [(PUBDEV-394)](https://0xdata.atlassian.net/browse/PUBDEV-394)
- GBM/GLM build model fails on Hadoop after building 100% => Failed to find schema for version: 3 and type: GBMModel [(PUBDEV-378)](https://0xdata.atlassian.net/browse/PUBDEV-378)
- Parsing KDD wrong [(PUBDEV-393)](https://0xdata.atlassian.net/browse/PUBDEV-393)
- GLM AIOOBE [(PUBDEV-199)](https://0xdata.atlassian.net/browse/PUBDEV-199)
- Flow : Build GLM Model with family poisson => java.lang.ArrayIndexOutOfBoundsException: 1 at hex.glm.GLM$GLMLambdaTask.needLineSearch(GLM.java:359) [(PUBDEV-210)](https://0xdata.atlassian.net/browse/PUBDEV-210)
- Flow : GLM Model Error => Enum conversion only works on small integers [(PUBDEV-365)](https://0xdata.atlassian.net/browse/PUBDEV-365)
- GLM binary response, do_classfication=FALSE, family=binomial, prediction error [(PUBDEV-339)](https://0xdata.atlassian.net/browse/PUBDEV-339)
- Epsilon missing from GLM parameters [(PUBDEV-354)](https://0xdata.atlassian.net/browse/PUBDEV-354)
- GLM NPE [(PUBDEV-395)](https://0xdata.atlassian.net/browse/PUBDEV-395)
- Flow: GLM bug (or incorrect output) [(PUBDEV-252)](https://0xdata.atlassian.net/browse/PUBDEV-252)
- GLM binomial prediction failing [(PUBDEV-403)](https://0xdata.atlassian.net/browse/PUBDEV-403)
- GLM binomial on benign.csv gets assertion error in predict [(PUBDEV-132)](https://0xdata.atlassian.net/browse/PUBDEV-132)
- current summary default_pctiles doesn't have 0.001 and 0.999 like h2o1 [(PUBDEV-94)](https://0xdata.atlassian.net/browse/PUBDEV-94)
- Flow: Build GBM/DL Model: java.lang.IllegalArgumentException: Enum conversion only works on integer columns [(PUBDEV-213)](https://0xdata.atlassian.net/browse/PUBDEV-213) [(github)](https://github.com/h2oai/h2o-dev/commit/57d6d96e4fed0a993bc8017f6e5eb1f60e9ceaa4)
- ModelMetrics on cup98VAL_z dataset has response with many nulls [(PUBDEV-214)](https://0xdata.atlassian.net/browse/PUBDEV-214)
- GBM : Predict model category output/inspect parameters shows as Regression when model is built with do classification enabled [(PUBDEV-441)](https://0xdata.atlassian.net/browse/PUBDEV-441)
- Fix double-precision DRF bugs [(github)](https://github.com/h2oai/h2o-dev/commit/cf7910e7bde1d8e3c1d91fadfcf37c5a74882145)

#####System
- Null columnTypes for /smalldata/arcene/arcene_train.data [(PUBDEV-406)](https://0xdata.atlassian.net/browse/PUBDEV-406) [(github)](https://github.com/h2oai/h2o-dev/commit/8511114a6ef6444938fb75e9ac9d5d7b7fe088d5)
- Flow: Waiting for -1 responses after starting h2o on hadoop cluster of 5 nodes [(PUBDEV-419)](https://0xdata.atlassian.net/browse/PUBDEV-419)
- Parse: airlines_all.csv => Airtime type shows as ENUM instead of Integer [(PUBDEV-426)](https://0xdata.atlassian.net/browse/PUBDEV-426) [(github)](https://github.com/h2oai/h2o-dev/commit/f6051de374b46376bf178064719fdd9b03e84dfa)
- Flow: Typo - "Time" option displays twice in column header type menu in Parse [(PUBDEV-446)](https://0xdata.atlassian.net/browse/PUBDEV-446)
- Duplicate validation messages in k-means output [(PUBDEV-305)](https://0xdata.atlassian.net/browse/PUBDEV-305) [(github)](https://github.com/h2oai/h2o-dev/commit/7905ba668572cb0eb518d791dc3262a2e8ff2fe0)
- Fixes Parse so that it returns to supplying generic column names when no column names exist [(github)](https://github.com/h2oai/h2o-dev/commit/d404bff2ef41e9a6e2d559c53c42225f11a81bff)
- Flow: Import File: File doesn't exist on all the hdfs nodes => Fails without valid message [(PUBDEV-313)](https://0xdata.atlassian.net/browse/PUBDEV-313)
- Flow: Parse => 1m.svm hangs at 42% [(HEXDEV-174)](https://0xdata.atlassian.net/browse/HEXDEV-174)
- Prediction NFE [(PUBDEV-308)](https://0xdata.atlassian.net/browse/PUBDEV-308)
- NPE doing Frame to key before it's fully parsed [(PUBDEV-79)](https://0xdata.atlassian.net/browse/PUBDEV-79)
- `h2o_master_DEV_gradle_build_J8` #351 hangs for past 17 hrs [(PUBDEV-239)](https://0xdata.atlassian.net/browse/PUBDEV-239)
- Sparkling water - container exited due to unavailable port [(PUBDEV-357)](https://0xdata.atlassian.net/browse/PUBDEV-357)



#####API
- Flow: Splitframe => java.lang.ArrayIndexOutOfBoundsException [(PUBDEV-410)](https://0xdata.atlassian.net/browse/PUBDEV-410) [(github)](https://github.com/h2oai/h2o-dev/commit/f5cf2888230df8904f0d87b8d97c31cc9cf26f79)
- Incorrect dest.type, description in /CreateFrame jobs [(PUBDEV-404)](https://0xdata.atlassian.net/browse/PUBDEV-404)
- space in windows filename on python [(PUBDEV-444)](https://0xdata.atlassian.net/browse/PUBDEV-444) [(github)](https://github.com/h2oai/h2o-dev/commit/c3a7f2f95ee41f5eb9bd9f4efd5b870af6cbc314)
- Python end-to-end data science example 1 runs correctly [(PUBDEV-182)](https://0xdata.atlassian.net/browse/PUBDEV-182)
- 3/NodePersistentStorage.json/foo/id should throw 404 instead of 500 for 'not-found' [(HEXDEV-163)](https://0xdata.atlassian.net/browse/HEXDEV-163)
- POST /3/NodePersistentStorage.json should handle Content-Type:multipart/form-data [(HEXDEV-165)](https://0xdata.atlassian.net/browse/HEXDEV-165)
- by class water.KeySnapshot$GlobalUKeySetTask; class java.lang.AssertionError: --- Attempting to block on task (class water.TaskGetKey) with equal or lower priority. Can lead to deadlock! 122 <= 122 [(PUBDEV-92)](https://0xdata.atlassian.net/browse/PUBDEV-92)
- Sparkling water : val train:DataFrame = prostateRDD => Fails with ArrayIndexOutOfBoundsException [(PUBDEV-392)](https://0xdata.atlassian.net/browse/PUBDEV-392)
- Flow : getModels produces error: Error calling GET /3/Models.json [(PUBDEV-254)](https://0xdata.atlassian.net/browse/PUBDEV-254)
- Flow : Splitframe => java.lang.ArrayIndexOutOfBoundsException [(PUBDEV-410)](https://0xdata.atlassian.net/browse/PUBDEV-410)
- ddply 'Could not find the operator' [(HEXDEV-162)](https://0xdata.atlassian.net/browse/HEXDEV-162) [(github)](https://github.com/h2oai/h2o-dev/commit/5f5dca9b9fc7d7d4888af0ab7ddad962f0381993)
- h2o.table AIOOBE during NewChunk creation [(HEXDEV-161)](https://0xdata.atlassian.net/browse/HEXDEV-161) [(github)](https://github.com/h2oai/h2o-dev/commit/338d654bd2a80ddf0fba8f65272b3ba07237d2eb)
- Fix warning in h2o.ddply when supplying multiple grouping columns [(github)](https://github.com/h2oai/h2o-dev/commit/1a7adb0a1f1bffe7bf77e5332f6291d4325d6a7f)


---



###0.1.26.1051 - 2/13/15

####New Features

- Flow: Display alternate UI for splitFrames() [(PUBDEV-399)](https://0xdata.atlassian.net/browse/PUBDEV-399)


####Enhancements 

#####System
-  Embedded H2O config can now provide flat file (needed for Hadoop) [(github)](https://github.com/h2oai/h2o-dev/commit/62c344505b1c1c9154624fd9ca07d9b7217a9cfa)
- Don't logging GET of individual jobs to avoid filling up the logs [(github)](https://github.com/h2oai/h2o-dev/commit/9d4a8249ceda49fcc64b5111a62c7a86076d7ec9)

#####Algorithms
-  Increase GBM/DRF factor binning back to historical levels. Had been capped accidentally at nbins (typically 20), was intended to support a much higher cap. [(github)](https://github.com/h2oai/h2o-dev/commit/4dac6ba640818bf5d482e6352a5e6aa62214ca4b)
-  Tweaked rho heuristic in glm [(github)](https://github.com/h2oai/h2o-dev/commit/7aec116974eb14ad6c7d7002a23d952a11339b79)
-  Enable variable importances for autoencoders [(github)](https://github.com/h2oai/h2o-dev/commit/19751e56c11f4ab672d47aabde84cf73271925dd)
-  Removed `group_split` option from GBM
-  Flow: display varimp for GBM output [(PUBDEV-398)](https://0xdata.atlassian.net/browse/PUBDEV-398)
-  variable importance for GBM [(github)](https://github.com/h2oai/h2o-dev/commit/f5085c3964d87d5349f406d1cfcc81fa0b34a27f)
-  GLM in H2O-Dev may provide slightly different coefficient values when applying an L1 penalty in comparison with H2O1.

####Bug Fixes

#####Algorithms
- Fixed bug in GLM exception handling causing GLM jobs to hang [(github)](https://github.com/h2oai/h2o-dev/commit/966a58f93d6cf746a2d6ec205d070247e4aeda01)
- Fixed a bug in kmeans input parameter schema where init was always being set to Furthest [(github)](https://github.com/h2oai/h2o-dev/commit/419754634ea30f6b9d9e24a2c62730a3a3b25042)
- Fixed mean computation in GLM [(github)](https://github.com/h2oai/h2o-dev/commit/74d9314a2b73812fa6dab03de9e8ea67c8a4693e)
- Fixed kmeans.R [(github)](https://github.com/h2oai/h2o-dev/commit/a532a0c850cd3c48b281bd34f83adac9108ac885)
- Flow: Building GBM model fails with Error executing javascript [(PUBDEV-396)](https://0xdata.atlassian.net/browse/PUBDEV-396)

#####System
- DataFrame propagates absolute path to parser [(github)](https://github.com/h2oai/h2o-dev/commit/0fad77b63512f2a20e20c93830e036a32a7643fe)
- Fix flow shutdown bug [(github)](https://github.com/h2oai/h2o-dev/commit/a26bd190dac59750131a2284bdf46e77ad12b67e)


---

###0.1.26.1032 - 2/6/15

####New Features

#####General Improvements 

- better model output 
- support for Python client
- support for Maven
- support for Sparkling Water
- support for REST API schema 
- support for Hadoop CDH5 [(github)](https://github.com/h2oai/h2o-dev/commit/6a0feaebc9c7e253fe07b43dc383dfe4cbae2f29)



#####UI
- Display summary visualizations by default in column summary output cells [(PUBDEV-337)](https://0xdata.atlassian.net/browse/PUBDEV-337)
- Display AUC curve by default in binomial prediction output cells [(PUBDEV-338)](https://0xdata.atlassian.net/browse/PUBDEV-338)
- Flow: Implement About H2O/Flow with version information [(PUBDEV-111)](https://0xdata.atlassian.net/browse/PUBDEV-111)
- Add UI for CreateFrame [(PUBDEV-218)](https://0xdata.atlassian.net/browse/PUBDEV-218)
- Flow: Add ability to cancel running jobs [(PUBDEV-373)](https://0xdata.atlassian.net/browse/PUBDEV-373)
- Flow: warn when user navigates away while having unsaved content [(PUBDEV-322)](https://0xdata.atlassian.net/browse/PUBDEV-322)





#####Algorithms
- Implement splitFrame() in Flow [(PUBDEV-356)](https://0xdata.atlassian.net/browse/PUBDEV-356)
- Variable importance graph in Flow for GLM [(PUBDEV-360)](https://0xdata.atlassian.net/browse/PUBDEV-360)
- Flow: Implement model building form init and validation [(PUBDEV-102)](https://0xdata.atlassian.net/browse/PUBDEV-102)
- Added a shuffle-and-split-frame function; Use it to build a saner model on time-series data [(github)](https://github.com/h2oai/h2o-dev/commit/730c8d64316c913183a1271d1a2441f92fa11442)
- Added binomial model metrics [(github)](https://github.com/h2oai/h2o-dev/commit/2d124bea91474f3f55eb5e33f2494ae52ffba749)
- Run KMeans from R [(HEXDEV-105)](https://0xdata.atlassian.net/browse/HEXDEV-105)
- Be able to create a new GLM model from an existing one with updated coefficients [(HEXDEV-48)](https://0xdata.atlassian.net/browse/HEXDEV-48) 
- Run KMeans from Python [(HEXDEV-106)](https://0xdata.atlassian.net/browse/HEXDEV-106)
- Run Deep Learning Binomial from Flow [(HEXDEV-83)](https://0xdata.atlassian.net/browse/HEXDEV-83)
- Run KMeans from Flow [(HEXDEV-104)](https://0xdata.atlassian.net/browse/HEXDEV-104)
- Run Deep Learning from Python [(HEXDEV-85)](https://0xdata.atlassian.net/browse/HEXDEV-85)
- Run Deep Learning from R [(HEXDEV-84)](https://0xdata.atlassian.net/browse/HEXDEV-84)
- Run Deep Learning Multinomial from Flow [(HEXDEV-108)](https://0xdata.atlassian.net/browse/HEXDEV-108)
- Run Deep Learning Regression from Flow [(HEXDEV-109)](https://0xdata.atlassian.net/browse/HEXDEV-109)


#####API
- Flow: added REST API documentation to the web ui [(PUBDEV-60)](https://0xdata.atlassian.net/browse/PUB-60)
- Flow: Implement visualization API [(PUBDEV-114)](https://0xdata.atlassian.net/browse/PUBDEV-114)



#####System
- Dataset inspection from Flow [(HEXDEV-66)](https://0xdata.atlassian.net/browse/HEXDEV-66)
- Basic data munging (Rapids) from R [(HEXDEV-70)](https://0xdata.atlassian.net/browse/HEXDEV-70)
- Implement stack operator/stacking in Lightning [(HEXDEV-128)](https://0xdata.atlassian.net/browse/HEXDEV-128)





####Enhancements 


#####UI
- Added better message when h2o.init() not yet called (`No active connection to an H2O cluster. Try calling "h2o.init()"`) [(github)](https://github.com/h2oai/h2o-dev/commit/b6bbbcee5972624cecc56099c0f95e1b2dd67253)



#####Algorithms
- Updated column-based gradient task to use sparse interface [(github)](https://github.com/h2oai/h2o-dev/commit/de5685b7c8e109cc39b671ef0bfd016516145d30)
- Updated LBFGS (added progress monitor interface, updated some default params), added progress and job support to GLM lbfgs [(github)](https://github.com/h2oai/h2o-dev/commit/6b89bb9201a89df93c4131b7ba10a7d17b45d72e)
- Added pretty print [(github)](https://github.com/h2oai/h2o-dev/commit/ebc824f9b081b61337c88e52b682bf42d9825c97)
- Added AutoEncoder to R model categories [(github)](https://github.com/h2oai/h2o-dev/commit/7030e7f1fb5779c026e0eed48662571f03f13428)
- Added Coefficients table to GLM model [(github)](https://github.com/h2oai/h2o-dev/commit/a432337d9d8b6480efbdaf0a0ebdb2ca3ad3f91a)
- Updated glm lbfgs to allow for efficient lambda-search (l2 penalty only) [(github)](https://github.com/h2oai/h2o-dev/commit/302ee73916516f2a25f98d96d9dd8fbff324dc5d)
- Removed splitframe shuffle parameter [(github)](https://github.com/h2oai/h2o-dev/commit/27f030721ae71006da7f0cc66be28337973f78f8)
- Simplified model builders and added deeplearning model builder [(github)](https://github.com/h2oai/h2o-dev/commit/302c819ea3d7b623af1968a181614d51d7dc68ed)
- Add DL model outputs to Flow [(PUBDEV-372)](https://0xdata.atlassian.net/browse/PUBDEV-372)
- Flow: Deep Learning: Expert Mode [(PUBDEV-284)](https://0xdata.atlassian.net/browse/PUBDEV-284)
- Flow: Display multinomial and regression DL model outputs [(PUBDEV-383)](https://0xdata.atlassian.net/browse/PUBDEV-383)
- Display varimp details for DL models [(PUBDEV-381)](https://0xdata.atlassian.net/browse/PUBDEV-381)
- Make binomial response "0" and "1" by default [(github)](https://github.com/h2oai/h2o-dev/commit/f597d4958ff2200f68e2cead31f3a184bfcaa5f2)
- Add Coefficients table to GLM model [(github)](https://github.com/h2oai/h2o-dev/commit/a432337d9d8b6480efbdaf0a0ebdb2ca3ad3f91a)
- Removed splitframe shuffle parameter [(github)](https://github.com/h2oai/h2o-dev/commit/27f030721ae71006da7f0cc66be28337973f78f8)
-  Update R GBM demos to reflect new input parameter names [(github)](https://github.com/h2oai/h2o-dev/commit/8cb99b5bf5ba828d08deba4647309824829a27a5)
-  Rename GLM variable importance to normalized coefficient magnitudes [(github)](https://github.com/h2oai/h2o-dev/commit/8cb99b5bf5ba828d08deba4647309824829a27a5)




#####API
- Changed `key` to `destination_key` [(github)](https://github.com/h2oai/h2o-dev/commit/22067ae62a23af712d3081d981ae08756e6c071e)
- Cleaned up REST API schema interface [(github)](https://github.com/h2oai/h2o-dev/commit/ce581ec9fe670f43e8fb4aa955569cc9e92d013b)
- Changed method name, cleaned setup, added a pyunit runner [(github)](https://github.com/h2oai/h2o-dev/commit/26ea2c52440dd6ad8009c72bac8057d1edd9da0a)





#####System
- Allow changing column types during parse-setup [(PUBDEV-376)](https://0xdata.atlassian.net/browse/PUBDEV-376)
- Display %NAs in model builder column lists [(PUBDEV-375)](https://0xdata.atlassian.net/browse/PUBDEV-375)
- Figure out how to add H2O to PyPl [(PUBDEV-178)](https://0xdata.atlassian.net/browse/PUBDEV-178)




####Bug Fixes


#####UI
- Flow: Parse => 1m.svm hangs at 42% [(PUBDEV-345)](https://0xdata.atlassian.net/browse/PUBDEV-345)
- cup98 Dataset has columns that prevent validation/prediction [(PUBDEV-349)](https://0xdata.atlassian.net/browse/PUBDEV-349)
- Flow: predict step failed to function [(PUBDEV-217)](https://0xdata.atlassian.net/browse/PUBDEV-217)
- Flow: Arrays of numbers (ex. hidden in deeplearning)require brackets [(PUBDEV-303)](https://0xdata.atlassian.net/browse/PUBDEV-303)
- Flow v.0.1.26.1030: StackTrace was broken [(PUBDEV-371)](https://0xdata.atlassian.net/browse/PUBDEV-371)
- Flow: Import files -> Search -> Parse these files -> null pointer exception [(PUBDEV-170)](https://0xdata.atlassian.net/browse/PUBDEV-170)
- Flow: "getJobs" not working [(PUBDEV-320)](https://0xdata.atlassian.net/browse/PUBDEV-320)
- Thresholds x Metrics and Max Criteria x Metrics tables were flipped in flow [(HEXDEV-155)](https://0xdata.atlassian.net/browse/HEXDEV-155)
- Flow v.0.1.26.1030: StackTrace is broken [(PUBDEV-348)](https://0xdata.atlassian.net/browse/PUBDEV-348)
- flow: getJobs always shows "Your H2O cloud has no jobs" [(PUBDEV-243)](https://0xdata.atlassian.net/browse/PUBDEV-243)
- Flow: First and last characters deleted from ignored columns [(PUBDEV-300)](https://0xdata.atlassian.net/browse/PUBDEV-300)
- Sparkling water => Flow => Menu buttons for cell do not show up [(PUBDEV-294)](https://0xdata.atlassian.net/browse/PUBDEV-294)




#####Algorithms
- Flow: Build K Means model with default K value gives error "Required field k not specified" [(PUBDEV-167)](https://0xdata.atlassian.net/browse/PUBDEV-167)
- Slicing out a specific data point is broken [(PUBDEV-280)](https://0xdata.atlassian.net/browse/PUBDEV-280)
- Flow: SplitFrame and grep in algorithms for flow and loops back onto itself [(PUBDEV-272)](https://0xdata.atlassian.net/browse/PUBDEV-272)
- Fixed the predict method [(github)](https://github.com/h2oai/h2o-dev/commit/10e6b88147791ef0e7e010ffad36bb3eb2969c7b)
- Refactor ModelMetrics into a different class for Binomial [(github)](https://github.com/h2oai/h2o-dev/commit/014d14c13fee5b87bdde1cb8b441c67def1365cc)
- /Predictions.json did not cache predictions [(HEXDEV-119)](https://0xdata.atlassian.net/browse/HEXDEV-119)
- Flow, DL: Error after changing hidden layer size [(PUBDEV-323)](https://0xdata.atlassian.net/browse/PUBDEV-323)
- Error in node$h2o#node: $ operator is invalid for atomic vectors [(PUBDEV-348)](https://0xdata.atlassian.net/browse/PUBDEV-348)
- Fixed K-means predict [(PUBDEV-321)](https://0xdata.atlassian.net/browse/PUBDEV-321)
- Flow: DL build mode fails => as it's missing adding quotes to parameter [(PUBDEV-301)](https://0xdata.atlassian.net/browse/PUBDEV-301)
- Flow: Build K means model with training/validation frames => unknown error [(PUBDEV-185)](https://0xdata.atlassian.net/browse/PUBDEV-185)
- Flow: Build quantile mode=> Click goes in loop [(PUBDEV-188)](https://0xdata.atlassian.net/browse/PUBDEV-188)





#####API
- Sparkling Water/Flow: Failed to find version for schema [(PUBDEV-367)](https://0xdata.atlassian.net/browse/PUBDEV-367)
- Cloud.json returns odd node name [(PUBDEV-259)](https://0xdata.atlassian.net/browse/PUBDEV-259)





#####System
- guesser needs to send types to parse [(PUBDEV-279)](https://0xdata.atlassian.net/browse/PUBDEV-279)
- Got h2o.clusterStatus function working in R. [(github)](https://github.com/h2oai/h2o-dev/commit/0d5a837f75145b3486e35eea198e322488e9afce)
- Parse: Using R => java.lang.NullPointerException [(PUBDEV-380)](https://0xdata.atlassian.net/browse/PUBDEV-380)
- Flow: Jobs => click on destination key => unimplemented: Unexpected val class for Inspect: class water.fvec.DataFrame [(PUBDEV-363)](https://0xdata.atlassian.net/browse/PUBDEV-363)
- Column assignment in R exposes NullPointerException in Rollup [(PUBDEV-155)](https://0xdata.atlassian.net/browse/PUBDEV-155)
- import from hdfs doesn't add files [(PUBDEV-260)](https://0xdata.atlassian.net/browse/PUBDEV-260)
- AssertionError: ERROR: got tcp resend with existing in-progress task [(PUBDEV-219)](https://0xdata.atlassian.net/browse/PUBDEV-219)
- HDFS parse fails when H2O launched on Spark CDH5 [(PUBDEV-138)](https://0xdata.atlassian.net/browse/PUBDEV-138)
- Flow: Parse failure => java.lang.ArrayIndexOutOfBoundsException [(PUBDEV-296)](https://0xdata.atlassian.net/browse/PUBDEV-296)
- "predict" step is not working in flow [(PUBDEV-202)](https://0xdata.atlassian.net/browse/PUBDEV-202)
- Flow: Frame finishes parsing but comes up as null in flow [(PUBDEV-270)](https://0xdata.atlassian.net/browse/PUBDEV-270)
- scala >flightsToORD.first() fails with "not serializable result" [(PUBDEV-304)](https://0xdata.atlassian.net/browse/PUBDEV-304)
- DL throws NPE for bad column names [(PUBDEV-15)](https://0xdata.atlassian.net/browse/PUBDEV-15)
- Flow: Build model: Not able to build KMeans/Deep Learning model [(PUBDEV-297)](https://0xdata.atlassian.net/browse/PUBDEV-297)
- Flow: Col summary for NA/Y cols breaks [(PUBDEV-325)](https://0xdata.atlassian.net/browse/PUBDEV-325)
- Sparkling Water : util.SparkUncaughtExceptionHandler: Uncaught exception in thread Thread NanoHTTPD Session,9,main [(PUBDEV-346)](https:/0xdata.atlassian.net/browse/PUBDEV-346)
- toDataFrame doesn't support sequence format schema (array, vectorUDT) [(PUBDEV-457)](https://0xdata.atlassian.net/browse/PUBDEV-457)





---

###0.1.20.1019 - 1/19/15

####New Features

#####UI
- Added various documentation links to the build page [(github)](https://github.com/h2oai/h2o-dev/commit/df222484f4bd4a48b7e1ca896b0e0c89bcf534b2)

#####Algorithms
- Ported matrix multiply over and connected it to rapids [(github)](https://github.com/h2oai/h2o-dev/commit/7361da8ff7e290b4bc3bdcc476d398147bf3d40e)

####Enhancements 

#####UI
- Allow user to specify (the log of) the number of rows per chunk for a new constant chunk; use this new function in CreateFrame [(github)](https://github.com/h2oai/h2o-dev/commit/3a35f88405a378391756d0550da5946ae59ba8f4)
- Make CreateFrame non-blocking, now displays progress bar in Flow [(github)](https://github.com/h2oai/h2o-dev/commit/991bfd8491e6b72d953b4539e7ba4973fa738a7c)
- Add row and column count to H2OFrame show method [(github)](https://github.com/h2oai/h2o-dev/commit/b541d092e5db83ac810ba9b5dab3c0e7e0053938)
- Admin watermeter page [(PUBDEV-234)](https://0xdata.atlassian.net/browse/PUBDEV-234)
- Admin stack trace [(PUBDEV-228)](https://0xdata.atlassian.net/browse/PUBDEV-228)
- Admin profile [(PUBDEV-227)](https://0xdata.atlassian.net/browse/PUBDEV-227)
- Flow: Add download logs in UI [(PUBDEV-204)](https://0xdata.atlassian.net/browse/PUBDEV-204)
- Need shutdown, minimally like h2o [(PUBDEV-74)](https://0xdata.atlassian.net/browse/PUBDEV-74)

#####API
- Changed 2 to 3 for JSON requests [(github)](https://github.com/h2oai/h2o-dev/commit/5dec9669cb71cf0e9f39154aef47403c82656aaf)
- Rename some more fields per consistency (`max_iters` changed to `max_iterations`, `_iters` to `_iterations`, `_ncats` to `_categorical_column_count`, `_centersraw` to `centers_raw`, `_avgwithinss` to `tot_withinss`, `_withinmse` to `withinss`) [(github)](https://github.com/h2oai/h2o-dev/commit/5dec9669cb71cf0e9f39154aef47403c82656aaf)
- Changed K-Means output parameters (`withinmse` to `within_mse`, `avgss` to `avg_ss`, `avgbetweenss` to `avg_between_ss`) [(github)](https://github.com/h2oai/h2o-dev/commit/cd24020b03c772c3ffcde9d97f84687cf1c32ce2)
- Remove default field values from DeepLearning parameters schema, since they come from the backing class [(github)](https://github.com/h2oai/h2o-dev/commit/ac1c8bb1c19d5a18d38463c25a2e4e785a71a0cc)
- Add @API help annotation strings to JSON model output [(PUBDEV-216)](https://0xdata.atlassian.net/browse/PUBDEV-216)

#####Algorithms
- Minor fix in rapids matrix multiplicaton [(github)](https://github.com/h2oai/h2o-dev/commit/a5d171ae4de00ce62768731781317a57074f0a09)
- Updated sparse chunk to cut off binary search for prefix/suffix zeros [(github)](https://github.com/h2oai/h2o-dev/commit/61f07672a1c7511e6e860488f6800341431627a1)
- Updated L_BFGS for GLM - warm-start solutions during lambda search, correctly pass current lambda value, added column-based gradient task [(github)](https://github.com/h2oai/h2o-dev/commit/b954c40c27cf22a56fd2995ae238fe6c18fba9bb)
- Fix model parameters' default values in the metadata [(github)](https://github.com/h2oai/h2o-dev/commit/dc0ac668c396e4c33ea6cedd304b0c04eb391755) 
- Set default value of k = number of clusters to 1 for K-Means [(PUBDEV-251)](https://0xdata.atlassian.net/browse/PUBDEV-251)

#####System
- Reject any training data with non-numeric values from KMeans model building [(github)](https://github.com/h2oai/h2o-dev/commit/52dcc2275c733f98fdfdfb430e02341e90a68063)

####Bug Fixes


#####API

- Fixed isSparse call for constant chunks [(github)](https://github.com/h2oai/h2o-dev/commit/1debf0d612d40f9707b43781c4561b87ee93f2df)
- Fixed sparse interface of constant chunks (no nonzero if const 1= 0) [(github)](https://github.com/h2oai/h2o-dev/commit/db16d595e654cdb356810681e272e0e0175e89a7)


#####System
- Typeahead for folder contents apparently requires trailing "/" [(github)](https://github.com/h2oai/h2o-dev/commit/53331a3bccb499a905d39870dae0c46c9883492a)
- Fix build and instructions for R install.packages() style of installation; Note we only support source installs now [(github)](https://github.com/h2oai/h2o-dev/commit/cad188739fca3a482a1358093b2e22284d64abc2)
- Fixed R test runner h2o package install issue that caused it to fail to install on dev builds [(github)](https://github.com/h2oai/h2o-dev/commit/e83d0c97ed13ace4d7f36a3b9a53a4792042ab95)
 
---

###0.1.18.1013 - 1/14/15

####New Features

#####UI 

- Admin timeline [(PUBDEV-226)](https://0xdata.atlassian.net/browse/PUBDEV-226)
- Admin cluster status [(PUBDEV-225)](https://0xdata.atlassian.net/browse/PUBDEV-225)
- Markdown cells should auto run when loading a saved Flow notebook [(PUBDEV-87)](https://0xdata.atlassian.net/browse/PUBDEV-87)
- Complete About page to include info about the H2O version [(PUBDEV-223)](https://0xdata.atlassian.net/browse/PUBDEV-223)

####Enhancements 

#####Algorithms

- Flow: Implement model output for GBM [(PUBDEV-119)](https://0xdata.atlassian.net/browse/PUBDEV-119)

---

###0.1.20.1016 - 12/28/14
- Added ip_port field in node json output for Cloud query [(github)](https://github.com/h2oai/h2o-dev/commit/641777855bc9f2c77d0d212eb3a8805452a01073)

---

