# Deep Water Python Tests

### What can Deep Water do?
* Train user-defined or pre-defined deeplearning models for image classification from Flow, R, Python, Java, Scala or REST API
* Train on a single GPU (requires CUDA) or CPU (requires BLAS) 
* Uses the MXNet backend transparently
* Behave just like any other H2O model (Flow, cross-validation, early stopping, hyper-parameter search, etc.)

### To run these examples:
* Install Ubuntu 16.04 LTS
* Install the latest NVIDIA Display driver
* Install CUDA 8 (latest available) in /usr/local/cuda
* Install CUDNN 5 (to lib and include directories in /usr/local/cuda/)
* Obtain GPU-enabled h2o.jar (preview: https://slack-files.com/T0329MHH6-F2GQ0B72S-bb15ff7626) - not strictly necessary, as h2o.jar is also in the python module below, but done here for simplicity (manual launch below)
* Obtain Deep Water edition of H2O's python module (preview: https://slack-files.com/T0329MHH6-F2GQH4D34-8d9295e775), install with `sudo pip install h2o*.whl`
* Optional (only for custom networks) - Obtain mxnet python egg (preview: https://slack-files.com/T0329MHH6-F2C7LQWMR-6b78dfab1a), install with `sudo easy_install <egg-file>`
* Set environment variables: `export CUDA_PATH=/usr/local/cuda` and `export LD_LIBRARY_PATH=$CUDA_PATH/lib64:$LD_LIBRARY_PATH`
* Run `java -jar h2o.jar`
* Download dataset (https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/deepwater/imagenet/cat_dog_mouse.tgz, unpack contents into directory ./bigdata/laptop/deepwater/imagenet/<here>, relative to where h2o was launched)
* Run python tests with `python ./pyunit_lenet_deepwater.py`

### Example: Inception model (152-layers)
![inception](./inception.png "Inception model")
