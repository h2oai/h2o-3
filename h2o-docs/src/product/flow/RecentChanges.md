#Recent Changes

##H2O-Dev

###0.1.20.1019 - 1/19/15

####New Features

These features have been added since the last release: 

#####UI
- Added various documentation links to the build page

#####API
- None

#####Algorithms
- Ported matrix multiply over and connected it to rapids

#####System
- None

---

####Enhancements 

These changes are to improvements to existing features (which includes changed default values):

#####UI
- Allow user to specify (the log of) the number of rows per chunk for a new constant chunk; use this new function in CreateFrame
- Make CreateFrame non-blocking, now displays progress bar in Flow
- Add row and column count to H2OFrame show method

#####API
- Changed 2 to 3 for JSON requests
- Rename some more fields per consistency (`max_iters` changed to `max_iterations`, `_iters` to `_iterations`, `_ncats` to `_categorical_column_count`, `_centersraw` to `centers_raw`, `_avgwithinss` to `avg_within_ss`, `_withinmse` to `within_mse`, )
- Changed K-Means output parameters (`withinmse` to `within_mse`, `avgss` to `avg_ss`, `avgbetweenss` to `avg_between_ss`)
- Remove default field values from DeepLearning parameters schema, since they come from the backing class
- Added ip_port field in node json output for Cloud query

#####Algorithms
- Minor fix in rapids matrix multiplicaton
- Updated sparse chunk to cut off binary search for prefix/suffix zeros
- Updated L_BFGS for GLM - warm-start solutions during lambda search, correctly pass current lambda value, added column-based gradient task
- Fix model parameters' default values in the metadata 
- Set default value of k = number of clusters to 1 for K-Means

#####System
- Reject any training data with non-numeric values from KMeans model building

---

####Bug Fixes

These changes are to resolve incorrect software behavior: 

#####UI
- None

#####API
- Fixed isSparse call for constant chunks
- Fixed sparse interface of constant chunks (no nonzero if const 1= 0)


#####Algorithms
- Fixed objective value in gradient solver (was missing penalty)


#####System
- Typeahead for folder contents apparently requires trailing "/"
- Fix build and instructions for R install.packages() style of installation; Note we only support source installs now
- Fixed R test runner h2o package install issue that caused it to fail to install on dev builds
 


