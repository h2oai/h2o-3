# H2O Developer Guide
This guide is meant to help developers become introduced to the H2O ML/Data Science platform and be able to get up to speed in creating new functions and ML algos. We will rely on actual code examples to illustrate how things work

## Outline
###H2O-3 Directory Structure
```
h2o-algos/
h2o-app/
h2o-bindings/
h2o-core/
h2o-dist/
h2o-docs/
h2o-docs-theme/
h2o-genmodel/
h2o-grpc/
h2o-hadoop/
h2o-kaggle/
h2o-parsers/
h2o-persist-hdfs/
h2o-persist-s3/
h2o-py/
h2o-r/
h2o-samples/
h2o-scala/
h2o-test-accuracy/
h2o-test-integ/
h2o-web/
```
`h2o-algos`H2O ML Algos
`h2o-core` The major parts of the H2O platform, Map Reduce, Rapids, Munging functions. Below this there is a `hex` directory for higher level classes related to model metrics and model training. Under `water` are lower level classes like the Frame,Vec,Chunk, all of Rapids. 

`h2o-genmodel` Helper functions for wrapping H2O Model POJOs
`h2o-py` H2O Python client code
`h2o-r` H2O R client code
###R/Python API
H2O has clients for R and Python to drive H2O various commands through REST calls. To the user, these calls looks like typical R/Python calls but on the client the commands are translated to an abstract syntax tree (AST) and sent as JSON to the H2O server.
###Rapids
###Frames,Vec,Chunks
###MRTask
###Models
###Flow

