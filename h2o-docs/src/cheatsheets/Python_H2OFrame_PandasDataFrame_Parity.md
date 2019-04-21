### Python H2OFrame / Pandas DataFrame Munging Converion Table

**Note**: A blank under the `Pandas Equivalent Method` means the method is equivalent to H2O. (Parenthesis are not always shown when need).

Last updated on 7/20/2017.  If you notice an missing method, please submit a pull request with the addition or post a message to the [h2ostream Google Group.](https://groups.google.com/forum/#!forum/h2ostream)

|  H2OFrame Method        |     Pandas Equivalent Method                           |                 
|-------------------------|-----------------------------------------------------                            
| .abs                     |                                                        | 
| .acos                    | .apply(lambda x: numpy.arccos(x), axis = 0)               | 
| .acosh                   | .apply(lambda x: numpy.arccosh(x), axis = 0)              | 
| .all                     |                                                        | 
| .any                     |                                                        | 
| .any\_na\_rm             |                                                        | 
| .anyfactor               |                                                        | 
| .apply                   | .apply                                                  | 
| .as\_data\_frame         |                                                        | 
| .as\_date               | .to\_datetime                                           | 
| .ascharacter             | astype(str)                                            | 
| .asfactor                | .astype('category') or .astype('object')               | 
| .asin                    | .apply(lambda x: numpy.arcsin(x), axis = 0)               | 
| .asinh                   | .apply(lambda x: numpy.arcsinh(x), axis = 0)              | 
| .asnumeric               | astype(numpy.float) or apply(numpy.float)                    | 
| .atan                    | .apply(lambda x: numpy.arctan(x), axis = 0)               | 
| .atanh                   | .apply(lambda x: numpy.arctanh(x), axis = 0)              | 
| .categories              | .unique()                                              | 
| .cbind                   | .concat()                                                | 
| .ceil                    | .apply(numpy.ceil)                                        | 
| .col\_names              | .columns                                               | 
| .columns                 |                                                        | 
| .columns\_by\_type       | .select\_dtypes()                                      | 
| .concat                  |                                                        | 
| .cor                     | .corr                                                | 
| .cos                     | .apply(lambda x: numpy.arccoh(x), axis = 0)               | 
| .cosh                    | .apply(lambda x: numpy.arccos(x), axis = 0)               | 
| .cospi                   | .apply(lambda x: numpy.cos(numpy.pi * x), axis = 0)          | 
| .count                   |                                                        | 
| .countmatches            | .str.contains()                                        | 
| .cummax                  |                                                        | 
| .cummin                  |                                                        | 
| .cumprod                 |                                                        | 
| .cumsum                  |                                                        | 
| .cut                     |                                                        | 
| .day                     | Series.dt.day                                          | 
| .dayOfWeek               | DatetimeIndex(pandas\_dataframe[time\_column]).dayofweek | 
| .ddply                   |                                                        | 
| .describe                |                                                        | 
| .difflag1                | .diff                                                | 
| .digamma                 | scipy.special.digamma()                                | 
| .dim                     | .shape                                                  | 
| .drop                    |                                                        | 
| .entropy                 | NA                                                     | 
| .exp                     | numpy.exp()                                            | 
| .expm1                   | numpy.expm1()                                          | 
| .filter\_na\_cols        | NA                                                     | 
| .flatten                 |                                                        | 
| .floor                   | .apply(numpy.floor)                                       | 
| .frame                   | NA                                                     | 
| .frame\_id               | NA                                                     | 
| .from\_python            | NA                                                     | 
| .gamma                   | scipy.special.gamma()                                  | 
| .get\_frame              | NA                                                     | 
| .get\_frame\_data        | similar to the purpose of to\_csv()                                                
| .getrow                  | list(pandas\_dataframe.loc[0,:])                        | 
| .group\_by                | .groupby()                                               | 
| .gsub                    | .replace()                                                | 
| .head                    |                                                        | 
| .hist                    |                                                        | 
| .hour                    | DatetimeIndex(pandas\_dataframe[time\_column]).year      | 
| .ifelse                  | numpy.where()                                             | 
| .impute                  | NA                                                     | 
| .insert\_missing\_values   | NA                                                     | 
| .interaction             | NA                                                     | 
| .isax                    | NA                                                     | 
| .ischaracter             | .isinstance(pandas\_column, object)                      | 
| .isfactor                | NA                                                     | 
| .isin                    |                                                        | 
| .isna                    | .isnull                                                 | 
| .isnumeric               | NA                                                     | 
| .isstring                | .isinstance(pandas\_column, object)                      | 
| .kfold\_column            | NA                                                     | 
| .kurtosis                |                                                        | 
| .levels                  | .cat.categories, .unique()                             | 
| .lgamma                  | scipy.special.gammaln()                                | 
| .log                     | numpy.log()                                               | 
| .log10                   | numpy.log10()                                             | 
| .log1p                   | numpy.log1p()                                             | 
| .log2                    | numpy.log2()                                              | 
| .logical\_negation        | numpy.logical\_not()                                       | 
| .lstrip                  | .str.lstrip('')                                        | 
| .match                   |                                                        | 
| .max                     |                                                        | 
| .mean                    |                                                        | 
| .median                  |                                                        | 
| .merge                   |                                                        | 
| .min                     |                                                        | 
| .mktime                  |                                                        | 
| .mode                    | NA                                                     | 
| .modulo\_kfold\_column     | NA                                                     | 
| .moment                  | pd.to\_datetime()                                         | 
| .month                   | Series.dt.month                                         | 
| .mult                    | .dot                                                   | 
| .na\_omit                 | .dropna()                                              | 
| .nacnt                   | .isnull().sum()                                        | 
| .names                   | .columns                                               | 
| .nchar                   | .str.len()                                             | 
| .ncol                    | .shape[1]                                              | 
| .ncols                   | .shape[1]                                              | 
| .nlevels                 | .nunique()                                             | 
| .nrow                    | .shape[0]                                              | 
| .nrows                   | .shape[0]                                              | 
| .num\_valid\_substrings    |                                                        | 
| .pop                     |                                                        | 
| .prod                    |                                                        | 
| .quantile                |                                                        | 
| .rbind                   |                                                        | 
| .refresh                 |                                                        | 
| .relevel                 | NA                                                     | 
| .rep\_len                 | NA                                                     | 
| .round                   |                                                        | 
| .rstrip                  | .str.rstrip()                                          | 
| .runif                   | numpy.random.uniform()                                    | 
| .scale                   | sklearn.preprocessing.StandardScaler()                 | 
| .sd                      | .std                                                   | 
| .set\_level               | NA                                                     | 
| .set\_levels              | NA                                                     | 
| .set\_name                | .rename()                                              | 
| .set\_names               | .rename()                                              | 
| .shape                   |                                                        | 
| .show                    | NA                                                     | 
| .sign                    | numpy.sign()                                              | 
| .signif                  | NA                                                     | 
| .sin                     | .apply(lambda x: numpy.sin(x), axis = 0)                  | 
| .sinh                    | .apply(lambda x: numpy.sinh(x), axis = 0)                 | 
| .sinpi                   | .apply(lambda x: numpy.sin(numpy.pi * x, axis = 0)           | 
| .skewness                | .skew                                                  | 
| .split\_frame             | NA                                                     | 
| .sqrt                    | .apply(lambda x: numpy.sqrt(x), axis = 0)                 | 
| .ss                      | NA                                                     | 
| .stratified\_kfold\_column | sklearn.model\_selection.StratifiedKFold                | 
| .stratified\_split        | sklearn.model\_selection.StratifiedShuffleSplit         | 
| .strsplit                | .str.split                                             | 
| .structure               | NA                                                     | 
| .sub                     | .str.replace()                                          | 
| .substring               | .str.slice()                                           | 
| .sum                     |                                                        | 
| .summary                 | .describe()                                            | 
| .table                   | .value\_counts()                                        | 
| .tail                    |                                                        | 
| .tan                     | .apply(lambda x: numpy.tan(x), axis = 0)                  | 
| .tanh                    | .apply(lambda x: numpy.tanh(x), axis = 0)                 | 
| .tanpi                   | .apply(lambda x: numpy.tan(numpy.pi * x, axis = 0)           | 
| .tolower                 |                                                        | 
| .toupper                 | .apply(lambda x: x.upper(), inplace=True)              | 
| .transpose               |                                                        | 
| .trigamma                | scipy.special.polygamma(x,3)                           | 
| .trim                    | .str.strip                                             | 
| .trunc                   |                                                        | 
| .type                    | .dtype                                                 | 
| .types                   | .dtypes                                                | 
| .unique                  |                                                        | 
| .var                     |                                                        | 
| .week                    | Series.dt.week                                         | 
| .which                   | NA                                                     | 
| .year                    | Series.dt.year                                         | 
