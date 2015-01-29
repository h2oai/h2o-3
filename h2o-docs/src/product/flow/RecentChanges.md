#Recent Changes 

##H2O-Dev

###0.1.20.1019 - 1/19/15

####New Features

These features have been added since the last release: 

#####UI
- Added various documentation links to the build page [(github)](https://github.com/h2oai/h2o-dev/commit/df222484f4bd4a48b7e1ca896b0e0c89bcf534b2)

#####API
- None

#####Algorithms
- Ported matrix multiply over and connected it to rapids [(github)](https://github.com/h2oai/h2o-dev/commit/7361da8ff7e290b4bc3bdcc476d398147bf3d40e)

#####System
- None

---

####Enhancements 

These changes are to improvements to existing features (which includes changed default values):

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
- Rename some more fields per consistency (`max_iters` changed to `max_iterations`, `_iters` to `_iterations`, `_ncats` to `_categorical_column_count`, `_centersraw` to `centers_raw`, `_avgwithinss` to `avg_within_ss`, `_withinmse` to `within_mse`) [(github)](https://github.com/h2oai/h2o-dev/commit/5dec9669cb71cf0e9f39154aef47403c82656aaf)
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

---

####Bug Fixes

These changes are to resolve incorrect software behavior: 

#####UI
- None

#####API
- Fixed isSparse call for constant chunks [(github)](https://github.com/h2oai/h2o-dev/commit/1debf0d612d40f9707b43781c4561b87ee93f2df)
- Fixed sparse interface of constant chunks (no nonzero if const 1= 0) [(github)](https://github.com/h2oai/h2o-dev/commit/db16d595e654cdb356810681e272e0e0175e89a7)


#####Algorithms
- None


#####System
- Typeahead for folder contents apparently requires trailing "/" [(github)](https://github.com/h2oai/h2o-dev/commit/53331a3bccb499a905d39870dae0c46c9883492a)
- Fix build and instructions for R install.packages() style of installation; Note we only support source installs now [(github)](https://github.com/h2oai/h2o-dev/commit/cad188739fca3a482a1358093b2e22284d64abc2)
- Fixed R test runner h2o package install issue that caused it to fail to install on dev builds [(github)](https://github.com/h2oai/h2o-dev/commit/e83d0c97ed13ace4d7f36a3b9a53a4792042ab95)
 
---

###0.1.20.1018 - 1/15/15

####New Features
These features have been added since the last release: 

#####UI 

- Admin timeline [(PUBDEV-226)](https://0xdata.atlassian.net/browse/PUBDEV-226)
- Admin cluster status [(PUBDEV-225)](https://0xdata.atlassian.net/browse/PUBDEV-225)
- Markdown cells should auto run when loading a saved Flow notebook [(PUBDEV-87)](https://0xdata.atlassian.net/browse/PUBDEV-87)
- Complete About page to include info about the H2O version [(PUBDEV-223)](https://0xdata.atlassian.net/browse/PUBDEV-223)

#####API

- None

#####Algorithms

- None

#####System

- None

---

####Enhancements 

These changes are to improvements to existing features (which includes changed default values):

#####UI

- None

#####API

- None

#####Algorithms

- Flow: Implement model output for GBM [(PUBDEV-119)](https://0xdata.atlassian.net/browse/PUBDEV-119)

#####System

- None

---

####Bug Fixes

These changes are to resolve incorrect software behavior: 

- None


###0.1.20.1016 - 12/28/14
- Added ip_port field in node json output for Cloud query [(github)](https://github.com/h2oai/h2o-dev/commit/641777855bc9f2c77d0d212eb3a8805452a01073)




