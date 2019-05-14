
# Target Encoding in Depth

Target encoding (TE), a.k.a. mean encoding, is a way of encoding categorical features into numerical ones. We are not going to discuss here in detail why would anyone want to apply this methodic to their dataset, but briefly, most common reasons are:

 - The model does not work with categorical variables, and high cardinality of the variable makes one-hot-encoding (OHE) a not very attractive option. (We will not comment on questions like whether TE is more preferable than OHE or under which conditions it starts to be true. This is an area for research and is outside of this blogpost's scope.)
 - We want to transfer knowledge that is available in a response column.

## Naming Considerations

"Target encoding" emphasizes the fact that the target (response) variable will be used for calculations, whereas "mean encoding" highlights the way that those encodings will be computed. In this blog post, we will stick to the "target encoding" name.

## Purpose

One of the reasons for providing this blog is to share experiences that we had over our development period and through communications with our customers and users. Due to the fact that this concept is relatively young and there are not that many implementations available, our API is on its way to becoming classic. 

Even though comprehensive documentation on the subject is [available on our web site](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-munging/target-encoding.html), here we will try to highlight and explain the most confusing aspects that are probably inherent to the approach itself and not that easily eliminated by **the design**.
	
## Setup

The famous ["titanic"](https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv) dataset will be used to minimize the chances of distraction from an unfamiliar dataset. This dataset suits our needs as it could be used as a binary classification problem and also it has high cardinality categorical features. The Python API will be our main client used here, though I will sometimes compare it with our R implementation in order to emphasize some API differences. 

## Model or Not a Model?

Some people tend to use analogy with model training when it comes to the process of creating an encoding map. Half (or more) of the reason for this is that quite often this logic is hidden behind the `.fit()` method. I would say that, while being definitely useful on one hand, this analogy could also lead to confusion on the other hand. Fitting means that we are adjusting something based on something else. We can "fit" parameters of the model to the training data. In the case of target encoding, we are just computing something based on the data that we want to become a source of our encodings. So, strictly speaking, we are not "fitting" anything; we are just calculating an encoding map. Don't worry, I will explain later what exactly an encoding map is.


## Getting Started

Instead of providing detailed parameters descriptions (those who need this, please check the [docs](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-munging/target-encoding.html)), I will try to provide extra information that will help to choose a right setup.

First, we need to create an instance of the 
`TargetEncoder` class. 

```
targetEncoder = TargetEncoder(x=teColumns, y=responseColumnName, fold_column=foldColumnName, blended_avg=True, inflection_point=3, smoothing=1)
```

In the above, `x` and `y` are required parameters. Choosing `y` should be a simple task, but choosing columns that will be encoded requires some considerations. In general, as usual, it depends on your data, and you will have to experiment with different sets of columns. Our recommendation here is to choose some threshold for cardinality and apply TE only for those who happens to be above it.

To fill out rest of the parameters, we need to think ahead and decide which "data leakage prevention strategy" (or in the context of TE, just "strategy") we will want to use for our training dataset (the one that was used to calculate the encoding map) and determine whether we will use blending. (This will affect all datasets we will apply our encoder to.) I will dedicate separate paragraphs for both "data leakage prevention strategies" and "blending". The main point here is that these parameters are strategic, and we need to bear in mind what we have chosen here while applying our trained encoder. 
In addition, a `fold_column`  should be specified only if we will want to use `kfold` strategy. 

Two points to clarify here:
 
 - A fold column for TE does not have to be the same column that is probably being used in cross validation. Research is required to say whether it is beneficial to use different folds for generating an encoding map. Intuitively, it feels like reusing a fold column might increase the chance of overfitting as both the "encoding map" and the "trained model" will be produced based on the same out-of-fold data, and performance of the corresponding CV model will be calculated on the fold that received "leakage" through target encoding. [PICTURE] 
 - Users quite often name the fold column for TE something like `cv_fold_column` - note the `cv_` prefix here - even when they don't use cross validation as a method for measuring the model's performance, and this column was added just for target encoding. This prefix indicates that users believe some cross validation is happening inside. So let's take a look at the "cross validation" term's etymology. This naming matches the fact that we are getting an estimate of the model (validation) by somehow combining estimates from multiple models being trained and tested on intersecting/crossed folds. With Target Encoding, we don't perform any validation at all. We just generate encodings based on out-of-fold data (which are, in fact, overlapping). So from my point of view, it is inaccurate to call this strategy, for example, "cross ~~validation~~ strategy." Instead, the better name would be "kfold" strategy.
 
Getting back to Target Encoder constructor's parameters, the rest of them are "blending" related. What is important at this moment is that the specified settings will be used for every dataset we are going to apply target encoding to. **Note**: In the R API, this is not the case as we don't have an object to store these parameter. Instead, we decide whether we want to use blending on a per-dataset basis later on.

## Creating an Encoding Map with .fit()

Two approaches could be distinguished:

 - When we want to reuse data for training that was previously used for generating the encoding map
 - When we compute encoding map on a holdout dataset that will not be used anywhere else

Code:

```
targetEncoder.fit(frame=train)
```

As a result, a computed encoding map will be stored in the `targetEncoder`.

## Understanding the Encoding Map Structure

In essence it is just a mapping from a categorical column name to a Frame that contains per-categorical-level terms for computing the final encodings. In the case where you provide a `fold_column` in TargetEncoder's constructor, the Frame in the encoding map will also contain an extra column with fold assignments. So `Column name` is a String key to get a corresponding frame. 


|      Column name                 |Level                         | Fold | Numerator | Denominator
|---------------|--------------|--------------|-------------|--------------|
|home.dest| a  | 1 |12 | 15
|           | a | 2 | 3 | 8
|           | b | 1 | 5 | 9

|      Column name                 |Level                         | Fold | Numerator | Denominator
|---------------|--------------|--------------|-------------|--------------|
|cabin| Q  | 1 | 2 | 4
|            | C  | 1 | 3 | 6

In the table above, **Numerator** is an aggregated sum of response values and **Denominator** is a count of instances within the corresponding levels.

## Data Leakage Prevention Strategies

There are three data leakage prevention strategies you can choose from in target encoding: 

 1. `kfold`: Encodings are computed based on the out-of-fold data.
 2. `loo`: The current row's response value will not be taken into account during encoding's calculations.
 3. `none`: All data is used.
 
The strategy should be specified only for an application/transformation step.

 ```
 targetEncoder.transform(..., holdout_type="kfold", ...)
 ```
 
You will want to use any of the first two strategies only in cases where you want to reuse the data from which the encoding map was generated. In all the other scenarios, `none` should be chosen.
 
Note that the `holdout_type` name is used for specifying the strategy because the strategy is all about which data you "hold out" from the encoding's computation.

## Blending

Blending is a way of balancing between posterior probability for a particular categorical level P(Y|X) and prior probability P(Y).

$$S_i = \lambda(n_i) {\ n_{iY} \over n_i} +  ( 1 - \lambda(n_i) ){\ n_{Y} \over n_{TR}}$$

where ${\ n_{iY} \over n_i}$ is a posterior term and ${\ n_{Y} \over n_{TR}}$ is a prior term.

To put it simple, let's say we have only one training instance in our training set for some categorical level. We can't rely on just that one row in saying anything for sure about frequencies of specific values' appearances in our response variable. This is why we would like to favor prior probability, which comes from the whole dataset. So blending is just the function that decides how much each of the probabilities contributes to the final encodings.

$$\lambda(n) = {\ 1 \over {1 + \exp (-{ \ n-inflection\_point \over smoothing}})}$$

There are two hyperparameters available for blending:

 - `inflection_point`
 - `smoothing`

Below is a plot of a simplified labmda function that depends only on `x` and has `smoothing = 1`:  

$$\lambda(n) = {\ 1 \over {1 + \exp ( - {\ x \over 1}})}$$

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=12YQrJIXRSLTuDqxAwUvV7Ny_EmNDoNPk"  height="330" />
</p>

From the plot, we can see that for $x = 0$ we will get $\lambda = 0.5$. (Meaning blending will equally favour `posterior` and `prior` probabilities.) But in our original function, we have `n - inflection_point` instead of `x`. So when `n` is equal to `inflection_point`, we will get $\lambda = 0.5$.

I would like to bring up a statement from an original paper:

> The parameter `k` determines half of the
minimal sample size for which we completely "trust" the estimate based on the sample in the cell.

**Note**: `k` is called `inflection_point` in our case, and complete "trust" means $\lambda$ should be close to `1`.

Let's check this statement. Consider two cases:

- `smoothing = 1`

 1) `inflection_point = 5` => `n = 10` => `x = n - inflection_point = 10 - 5 = 5` => $\lambda$ close to 1.

 2) `inflection_point = 100` => `n = 200` => `x = n - inflection_point = 200 - 100 = 100` => $\lambda$ close to 1.

 3) `inflection_point = 1` => `n = 2` => `x = n - inflection_point = 2 - 1 = 1` => $\lambda$ approximately 0.6.
 
- `smoothing != 1 `

	1) `smoothing = 100` and `inflection_point = 5` => `n = 10` => `x = n - inflection_point = 10 - 5 = 5` =>  It is clear that for `x = 5`  $\lambda$ **is not close** to 1 (see graph below)
	
	<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1ch-rZMRZhCe7EZ3_c9KG1fPipKcIJ48S"  height="330" />
</p>


So this statement holds true only under certain conditions, but it is not true in general (meaning there should not be such an intuition for understanding and choosing `inflection_point`'s value).

One mportant note about `inflection_point` to take away is that it determines when the sign of the exponent changes and the graph flips, as in the figure below. Lambda will be approaching 0 in this case, and `prior probabilities` term will be dominating.

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1OMz7oOUvon7Vi06Ds_kgfhsiHmkz1t2I"  height="280" />
</p>

> Example 1:
 `inflection_point = 5` and `n = 2` 
$$ {\ 1 \over {1 + \exp ( - {\ (2 -5) }})} = {\ 1 \over {1 + \exp ( + {\ 3 }})} \approx0$$ 


The size of the difference in `n - inflection_point` together with the `smoothing` parameter just defines how fast the transition will be from "completely prior probability" ( $\lambda = 0$ )  to "completely posterior probability" ( $\lambda = 1$ ).

Four plots below show how the labmda function changes when we increase the `smoothing` parameter from 1 to 80. 

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1cACgx2DdcrF0Zm5e-IE6U8Ixj4uvZ9hD"  height="330" />
</p>
	
	
## Applying TargetEncoder

```
encodedTrain = targetEncoder.transform(frame=train, holdout_type="kfold", seed=1234)
encodedValid = targetEncoder.transform(frame=valid, holdout_type="none", noise=0.0)
encodedTest = targetEncoder.transform(frame=test, holdout_type="none", noise=0.0)
```

Note that the `train` frame was the same used to generate the encoding map with the `.fit()` method.

Again, that is why we specify a `kfold` strategy to try to minimise data leakage. The fact that we need to first call `.fit(train)` and then `.tranform(train,...)` on the same data might be confusing, especially for those who have decided to use the analogy of model training.

### Noise

One more option to pay attention to is the `noise` parameter. The purpose of `noise` is to provide an extra tool against overfitting. This is sort of a dummy regularization, as it is just __random__ noise.

Similar to `holdout_type`, we want to apply non-zero noise only to the data that was used for `encoding_map` generation (in our case `train`). 

**Note**: If a dedicated holdout dataset was used for generating the `encoding_map`, then we should use `holdout_type="none"` and `noise=0.0` for each of our `train` / `valid` / `test` datasets.

By default, a noise of 0.01 is applied, but we still want to provide a `seed` for reproducibility purposes. It is important to set `noise = 0.0` explicitly for datasets that were not participating in the `encoding_map` generation.
	
## Output Features Names

Encoded versions of the categorical columns will have the suffix `_te` appended to its original names. For example, the column `embarked` will produce `embarked_te` and so on. See screenshoot below.

![](https://drive.google.com/uc?export=view&id=1tg3mKn-_Ggj5mtEOtlddEMOZLSKZasHe)


## End-to-End Benchmark

I'm going to run benchmark with 10 restarts (different seeds for splitting data) and compare the performance of GBMs that were trained on data with and without target encoding.

Here is a [jupyter notebook](https://drive.google.com/uc?export=view&id=1HBg_Bp8T9sRdRYGMN_bEy0hEzBitRg_t).


Here is a printout of the result:

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1LoI9JvpKxBTcsQHetvjEYV-woy6oZRnR"  height="900" />
</p>

**Note**: There are seeds available in the printout in case you want to reproduce the results.

## Summary


This shows us that with given model and fixed blending hyperparameters, we were able, on average, to outperform baseline performance.

```
Average AUX over 10 runs without TE:  0.8059  
Average AUX over 10 runs with TE:  0.8502
```

It is worth mentioning that we were not doing any search over blending's hyperparameters; we were just using fixed values `inflection_point = 3` and `smoothing = 1`. With Random Grid Search, we would expect to get even better results.


## References:

1. A Preprocessing Scheme for High-Cardinality Categorical
Attributes in Classification and Prediction Problems. Daniele Micci-Barreca
2. [Target Encoding h2o-3 documentation](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-munging/target-encoding.html)
