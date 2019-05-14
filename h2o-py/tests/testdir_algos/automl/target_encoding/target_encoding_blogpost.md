
# Target Encoding in depth

Target encoding (TE) a.k.a. mean encoding is a way of encoding categorical features into numerical ones. We are not going to discuss here in details why would anyone want to apply this methodic to their dataset. But briefly, most common reasons are:

 - model does not work with categorical variables and high cardinality of the variable makes one-hot-encoding(OHE) to be not very attractive option ( no comments on questions like whether TE is more preferable than OHE or under what conditions it starts to be true - this is an area for research and is outside of the blogpost's scope )
 - we want to transfer knowledge that is available in a response column 

## Naming considerations
`Target encoding` emphasise the fact that target (response) variable will be used for calculations whereas `mean encoding` highlights the way of how those encodings will be computed. We will stick to the `target encoding` name.

## Why?

One of the reasons of current post is to share experience that we have got over development period and through communication with our customers and users. Due to the fact that this concept is relatively young and there are not that many implementations out there API is still on its way of becoming classic. 
	Even though quite comprehensive documentation on the subject is [available](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-munging/target-encoding.html) on our web site here we will try to highlight and explain most confusing aspects that are probably inherent to the approach itself and not that easily eliminated by **the design**.
	
## Setup

Famous titanic dataset will be used to minimise chances of distraction from unfamiliar datasets. It suits our needs as it could be used as a binary classification problem and also it has high cardinality categorical features. Python API will be our main guest though I will sometimes compare it with our R implementation to emphasise some API differences. 

## Model or not a Model?

Some people tend to use analogy with model training when it comes to a process of creation of encoding map. Half (or maybe even 100%) of the reason for that is that quite often this logic is hidden behind the `.fit()` method. I would say that while being definitely useful on one hand this analogy could also lead to many confusions on the other hand.
Fitting means that we are adjusting something based on another something. We let's say can fit parameters of the model to the training data. In case of the target encoding we are just computing something based on the data that we want to become a source of our encodings. So, strictly speaking, we are not fitting anything but just calculating encoding map. Don't worry, I will explain later what exactly encoding map is.


## Getting started

Instead of providing detailed parameters' descriptions ( those who need it please check docs) I will try to give some extra information that will help to choose a right setup.

First, we need to create instance of `TargetEncoder` class. 
```
targetEncoder = TargetEncoder(x=teColumns, y=responseColumnName, fold_column=foldColumnName, blended_avg=True, inflection_point=3, smoothing=1)
```

`x` and `y` are required parameters. Choosing `y` should be a simple task but choosing columns that will be encoded requires some considerations. In general, as usual, it depends on the data and you will have to experiment with different sets of columns. Recommendation here is to choose some threshold for cardinality and apply TE only for those who happens to be above it.

To fill out rest of the parameters we need to to think ahead and decide which `data leakage prevention strategy` ( or in the context of TE just strategy) we will want to use for our `training` dataset ( the one that was used to calculate encoding map) and whether we will use blending ( this will affect all datasets we will apply out encoder to). I will dedicate separate paragraphs for both `data leakage prevention strategies` and `blending`. Main point here is that these parameters are strategic and we need to bear in mind what we have chosen here while applying our `trained` encoder. 
 `fold_column`  should be specified only if we will want to use `kfold` strategy. Two things to clarify here:
 
 - Fold column for TE does not have to be the same column with that which is probably being used in cross validation. Research required to say whether it is beneficial to use different folds for generation of encoding map or not. Intuitively it feels like reusing fold column might increase chances of overfitting as both `encoding map` and  `trained model` will be produced based on the same out-of-fold data and performance of the corresponding cv model will be calculated on the fold that received 'leakage` through target encoding. [PICTURE] 
 - users quite often call fold column for TE something like `cv_fold_column` - note `cv_` suffix here - even when they don't use cross validation as a method of measuring model's performance  and this column was added just for target encoding. It indicates that it is believed that some cross validation is happening inside. So let's take a look at `cross validation`  term's etymology. Naming matches the fact that we are getting estimate of the model (validation) by somehow combining estimates from multiple models being trained and tested on intersecting/crossed folds. With Target Encoding we don't do any validation at all. We just generate encodings based on out-of-fold data - which btw are overlapping. So from my point of view it is eligible to call this strategy for example `cross ~~validation~~ strategy` where the best name would be `kfold` strategy.
 
 Getting back to TargetEncoder constructor's parameters, rest of them are `blending` related. What is important at this moment is that specified settings will be used for every dataset we are going to apply target encoding to. 
 *Note: in R API it is not the case as we don't have an object to store these parameter into and have to(or able to) to decide whether we want to use blending or not on per dataset basis later on.*

## Creating encoding map with .fit()

Two approaches could be distinguished:

 - when we want to reuse data - that was used for encoding map's generation - for training
 - when we compute encoding map on a holdout dataset which will not be used anywhere else

Code:

```
targetEncoder.fit(frame=train)
```

As a result computed`encoding map` will be stored in the`targetEncoder` 

## Understanding `encoding map`'s structure

In essence it is just a mapping from categorical column name to a Frame that contains per-categorical-level terms for computing final encodings. In case of providing `fold_column` in TargetEncoder's constructor, Frame in the `encoding map` will also contain an extra column with fold assignments. So `Column name` is a String key to get corresponding frame. 


|      Column name                 |Level                         | Fold | Numerator | Denominator
|---------------|--------------|--------------|-------------|--------------|
|home.dest| a  | 1 |12 | 15
|           | a | 2 | 3 | 8
|           | b | 1 | 5 | 9

|      Column name                 |Level                         | Fold | Numerator | Denominator
|---------------|--------------|--------------|-------------|--------------|
|cabin| Q  | 1 | 2 | 4
|            | C  | 1 | 3 | 6

`Numerator` is an aggregated sum of response values and `Denominator` is a count of instances within corresponding levels.

## Data leakage prevention strategies

There are three strategy you can choose from in target encoding: 

 1. `kfold` - encodings are being computed based on the out-of-fold data
 2. `loo` - current row's response value will not be taken into account during encoding's calculations
 3. `none` - all data is being used
 
 
Strategy should be specified only for an application/transformation step.
 ```
 targetEncoder.transform(..., holdout_type="kfold", ...)
 ```
 
 One will want to use any of the first two strategies only in cases when we want to reuse data which `encoding map` was generated from. In all the other scenarios option `none` should be chosen.
 
 Note that `holdout_type` name is being used for specifying the strategy as, in essence, it is all about which data you hold out from the encoding's computation.

## Blending

This is a way of balancing between posterior probability for a particular categorical level P(Y|X) and prior probability P(Y).

$$S_i = \lambda(n_i) {\ n_{iY} \over n_i} +  ( 1 - \lambda(n_i) ){\ n_{Y} \over n_{TR}}$$

where ${\ n_{iY} \over n_i} $ is a posterior term and ${\ n_{Y} \over n_{TR}} $ is a prior term.

 To put it simple let's say we have got only one training instance in our training set for some categorical level. We can't rely on just that one row in saying anything for sure about frequencies of specific values appearances in our response variable. That is why we would like to favour prior probability which come from the whole dataset . So blending is just about function that decides how much each of the probabilities contributes to the final encodings.

$$\lambda(n) = {\ 1 \over {1 + \exp (-{ \ n-inflection\_point \over smoothing}})}$$


There are two hyperparameters for Blending:

 - `inflection_point`
 - `smoothing`

Below is a plot of simplified labmda function that depends only on `x` and has `smoothing = 1`:  

$$\lambda(n) = {\ 1 \over {1 + \exp ( - {\ x \over 1}})}$$

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=12YQrJIXRSLTuDqxAwUvV7Ny_EmNDoNPk"  height="330" />
</p>

From the plot we can see that for $x = 0$ we will get $\lambda = 0.5$ (meaning blending will equally favour `posterior` and `prior` probabilities). But in our original function we have `n - inflection_point` instead of `x`. So when `n` is equal to `inflection_point` we will get $\lambda = 0.5$.

I would like to bring up a statement from an original paper :

> The parameter k determines half of the
minimal sample size for which we completely “trust” the estimate
based on the sample in the cell.

*Note: `k` is called `inflection_point` in our case. Complete "trust" means $\lambda$ should be close to `1`.*

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



So this statement holds true only under certain conditions and is not true in general meaning there should not be such an intuition for understanding and choosing `inflection_point`'s value.

Important thing about `inflection_point` to take away is that it determines when the sign of the exponent changes and graph flips as on the figure below. Lambda will be approaching 0 in this case and `prior probabilities` term will be dominating.

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1OMz7oOUvon7Vi06Ds_kgfhsiHmkz1t2I"  height="280" />
</p>

> Example 1:
 `inflection_point = 5` and `n = 2` 
$$ {\ 1 \over {1 + \exp ( - {\ (2 -5) }})} = {\ 1 \over {1 + \exp ( + {\ 3 }})} \approx0$$ 


Size of the difference `n - inflection_point` together with `smoothing` parameter just define how fast will be a transition from `completely prior probability` ( $\lambda = 0$ )  to `completely posterior probability` ( $\lambda = 1$ ).

Four plots below show how labmda function changes when we increase `smoothing` parameter from 1 to 80. 

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1cACgx2DdcrF0Zm5e-IE6U8Ixj4uvZ9hD"  height="330" />
</p>
	
	
## Applying TargetEncoder

```
encodedTrain = targetEncoder.transform(frame=train, holdout_type="kfold", seed=1234)
encodedValid = targetEncoder.transform(frame=valid, holdout_type="none", noise=0.0)
encodedTest = targetEncoder.transform(frame=test, holdout_type="none", noise=0.0)
```
Note that `train` frame was used to generate `encoding map` with the `.fit()` method.

Again, that is why we specify `kfold` strategy to try to minimise dataleakage. The fact that we need to first call `.fit(train)` and then `.tranform(train,...)` on the same data might be confusing, especially for those who have decided to use analogy of model training.
### Noise
One more thing to pay attention to is `noise` parameter. The purpose of it is to provide an extra tool against overfitting. Sort of a dummy regularization as it is just __random__ noise.
Similar to `holdout_type` we only need it for `train` frame and only when . By default noise of 0.01 is being applied but we still want to provide `seed` for reproducibility purposes.
	
## Output features names

Encoded versions of the categorical columns will have suffix `_te` added to its original names. 

For example, column `embarked` will produce `embarked_te` and so on. See screenshoot below.


![](https://drive.google.com/uc?export=view&id=1tg3mKn-_Ggj5mtEOtlddEMOZLSKZasHe)


## End to end benchmark

I'm going to run benchmark with 10 restarts(different seeds for splitting data) and compare performance of GBMs that was trained on data with and without target encoding.

Here is a [jupyter notebook](https://drive.google.com/uc?export=view&id=1HBg_Bp8T9sRdRYGMN_bEy0hEzBitRg_t)


Here is a printout of the result:

<p align="center">
<img src="https://drive.google.com/uc?export=view&id=1LoI9JvpKxBTcsQHetvjEYV-woy6oZRnR"  height="900" />
</p>

*Note: there are seeds available in the printout in case you want to reproduce results.*

## Summary


__It shows us that with given model and fixed blending hyperparameters we were able, on average, to outperform baseline performance.__

```
Average AUX over 10 runs without TE:  0.8059  
Average AUX over 10 runs with TE:  0.8502
```


It is worth to mention that we were not doing any search over blending's hyperparameters but were just using fixed values `inflection_point = 3` and `smoothing = 1`. With Random Grid Search we would expect to get even better results.


## References:
1. A Preprocessing Scheme for High-Cardinality Categorical
Attributes in Classification and Prediction Problems. Daniele Micci-Barreca
2. [Target Encoding h2o-3 documentation](http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-munging/target-encoding.html)
