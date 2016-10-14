from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

import numpy as np
import time
import math

def make_text_cnn(sentence_size, num_embed, batch_size, vocab_size,
        num_label=2, filter_list=[3, 4, 5], num_filter=100,
        dropout=0., with_embedding=True):
    import mxnet as mx

    input_x = mx.sym.Variable('data') # placeholder for input
    input_y = mx.sym.Variable('softmax_label') # placeholder for output

    # embedding layer
    if not with_embedding:
        embed_layer = mx.sym.Embedding(data=input_x, input_dim=vocab_size, output_dim=num_embed, name='vocab_embed')
        conv_input = mx.sym.Reshape(data=embed_layer, target_shape=(batch_size, 1, sentence_size, num_embed))
    else:
        conv_input = input_x

    # create convolution + (max) pooling layer for each filter operation
    pooled_outputs = []
    for i, filter_size in enumerate(filter_list):
        convi = mx.sym.Convolution(data=conv_input, kernel=(filter_size, num_embed), num_filter=num_filter)
        relui = mx.sym.Activation(data=convi, act_type='relu')
        pooli = mx.sym.Pooling(data=relui, pool_type='max', kernel=(sentence_size - filter_size + 1, 1), stride=(1,1))
        pooled_outputs.append(pooli)

    # combine all pooled outputs
    total_filters = num_filter * len(filter_list)
    concat = mx.sym.Concat(*pooled_outputs, dim=1)
    h_pool = mx.sym.Reshape(data=concat, target_shape=(batch_size, total_filters))

    # dropout layer
    if dropout > 0.0:
        h_drop = mx.sym.Dropout(data=h_pool, p=dropout)
    else:
        h_drop = h_pool

    # fully connected
    cls_weight = mx.sym.Variable('cls_weight')
    cls_bias = mx.sym.Variable('cls_bias')

    fc = mx.sym.FullyConnected(data=h_drop, weight=cls_weight, bias=cls_bias, num_hidden=num_label)

    # softmax output
    sm = mx.sym.SoftmaxOutput(data=fc, label=input_y, name='softmax')
    return sm



def deepwater_tweets():
  if not H2ODeepWaterEstimator.available(): return

  tweets = h2o.import_file(pyunit_utils.locate("/home/arno/tweets.txt"), col_names=["text"], sep="|")
  labels = h2o.import_file(pyunit_utils.locate("/home/arno/labels.txt"), col_names=["label"])
  frame = tweets.cbind(labels)
  print(frame.head(5))

#  cnn = make_text_cnn(sentence_size=100, num_embed=300, batch_size=32,
#            vocab_size=100000, dropout=dropout, with_embedding=with_embedding)
  model = H2ODeepWaterEstimator(epochs=50000, learning_rate=1e-3, hidden=[100,100,100,100,100])
  model.train(x=[0],y=1, training_frame=frame)
  model.show()
  error = model.model_performance(train=True).mean_per_class_error()
  assert error < 0.1, "mean classification error is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_tweets)
else:
  deepwater_tweets()
