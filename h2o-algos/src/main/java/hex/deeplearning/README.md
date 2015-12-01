#H2O Deep Learning

* [Download H2O (Instructions)](http://h2o.ai/download)
* [H2O Deep Learning Booklet (Documentation)](http://h2o.ai/resources)
* [H2O Deep Learning Top 10 Tips & Tricks (Slides)](http://www.slideshare.net/0xdata/h2o-world-top-10-deep-learning-tips-tricks-arno-candel)
* [H2O World 2015 Recordings (Slides and Videos)](http://h2oworld.h2o.ai)
* [H2O World 2015 Training Material (Tutorial)](https://github.com/h2oai/h2o-world-2015-training/tree/master/tutorials/deeplearning)
* [H2O R Unit Tests (Code examples)](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/deeplearning)
* [H2O Python Unit Tests (Code examples)](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/deeplearning)
* [H2O Deep Learning Performance Tuning Guide (Blog)](http://h2o.ai/blog/2015/08/deep-learning-performance/)
* [H2O Deep Learning on 150M rows on 8-node EC2 Cluster (Video)](https://www.youtube.com/watch?v=bInMSgZhDd4)
* [H2O Deep Learning Presentation (Video)](https://www.youtube.com/watch?v=E7aWAf-2N98)
* [H2O Deep Learning Presentation (Slides)](http://www.slideshare.net/0xdata/arno-candel-scalabledatascienceanddeeplearningwithh2omeetupacmebay)

## Results

While H2O Deep Learning is written in pure Java, it is *competitive* with other CPU and GPU based solutions for common multi-layer feed-forward neural networks. H2O is distributed and can handle large datasets that don't fit on any single node's memory. Dedicated GPU-based solutions can be faster for large neural networks (millions of weights) and for convolutional/LSTM-based architectures (which are not currently supported by H2O), especially for image or NLP applications. H2O Deep Learning is particularly well suited for structured data and use cases include fraud, churn, insurance, finance, marketing, sciences and many more.

### MNIST
The [MNIST](http://yann.lecun.com/exdb/mnist/) database of handwritten digits has a training set of 60,000 examples, and a test set of 10,000 examples. Each digit is represented by 28x28=784 gray-scale pixel values (features).

The following benchmark is available as an [example Flow pack](https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/flow/packs/examples) and is also part of the distribution. In Flow, on the right-hand-side, click on the 'HELP' tab, then 'view example Flows', then select 'DeepLearning_MNIST.flow'.

**Model parameters**: For illustration only, not tuned. 2 hidden layers (128,64), Rectifier with Dropout, L1/L2 regularization, mini-batch size 1. Auto-tuning for the number of training images per Map/Reduce iteration (`train_samples_per_iteration=-2`). The model is trained until convergence of the test set accuracy, and scored on the training and test sets every 5 seconds, with full confusion matrices and variable importances.

**Hardware**: Dual Xeon E5-2650 2.6GHz, Ubuntu 12.04, Java 7, 10GbE interconnect

| | test set error | speed | 
| --- | ---: | ---: |
| H2O 1 node | 2.1% | 80K images/sec |
| H2O 2 nodes | 2.1% | 140K images/sec |
| H2O 4 nodes | 2.1% | 280K images/sec | 
| H2O 8 nodes | 2.1% | 550K images/sec |
| 1 GPU GTX980 (pick your tool)| | ~100K images/sec |
