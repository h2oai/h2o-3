# Deep Water Python Tests

### What can Deep Water do?
* Train user-defined or pre-defined deeplearning models for image classification
* Train on a single GPU (requires CUDA) or CPU (requires BLAS) 
* Uses the MXNet backend transparently
* Behave just like any other H2O model (Flow, cross-validation, early stopping, hyper-parameter search, etc.)

### To run these examples:
* Install Ubuntu 16.04 LTS
* Install CUDA 8 in /usr/local/cuda
* Install CUDNN 5 (place files in /usr/local/cuda/lib64/)
* Obtain GPU-enabled h2o.jar (preview: https://slack-files.com/T0329MHH6-F2C9B5KGF-6472650a90) - not strictly necessary, as h2o.jar is also in the python module below, but done here for simplicity (manual launch below)
* Obtain Deep Water edition of H2O's python module (preview: https://slack-files.com/T0329MHH6-F2C9LUFHN-2ebff8798e), install with `sudo pip install h2o*.whl`
* Obtain mxnet python egg (preview: https://slack-files.com/T0329MHH6-F2C7LQWMR-6b78dfab1a), install with `sudo easy_install <egg-file>`
* Run with `CUDA_path=/usr/local/cuda java -jar h2o.jar`
* Download dataset (https://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/deepwater/imagenet/cat_dog_mouse.tgz, unpack contents into directory ./bigdata/laptop/deepwater/imagenet/<here>, relative to where h2o was launched)
* Run python tests with `python ./pyunit_lenet_deepwater.py`

### Example: Inception model (152-layers)
![inception](./inception.png "Inception model")
