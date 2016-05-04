Glossary
========

+---------------+----------------+
| Term          | Definition     |
+===============+================+
| **H2O.ai**    | Maker of H2O.  |
|               | Visit our      |
|               | `website <http |
|               | ://h2o.ai>`__. |
+---------------+----------------+
| **Autoencoder | An extension   |
| **            | of the Deep    |
|               | Learning       |
|               | framework. Can |
|               | be used to     |
|               | compress input |
|               | features       |
|               | (similar to    |
|               | PCA). Sparse   |
|               | autoencoders   |
|               | are simple     |
|               | extensions     |
|               | that can       |
|               | increase       |
|               | accuracy. Use  |
|               | autoencoders   |
|               | for:- generic  |
|               | dimensionality |
|               | reduction (for |
|               | pre-processing |
|               | for any        |
|               | algorithm)-    |
|               | anomaly        |
|               | detection (for |
|               | comparing the  |
|               | reconstructed  |
|               | signal with    |
|               | the original   |
|               | to find        |
|               | differences    |
|               | that may be    |
|               | anomalies)-    |
|               | layer-by-layer |
|               | pre-training   |
|               | (using stacked |
|               | auto-encoders) |
+---------------+----------------+
| **Backpropoga | Uses a known,  |
| tion**        | desired output |
|               | for each input |
|               | value to       |
|               | calculate the  |
|               | loss function  |
|               | gradient for   |
|               | training. If   |
|               | enabled,       |
|               | performed      |
|               | after each     |
|               | training       |
|               | sample in      |
|               | `**Deep        |
|               | Learning** <#D |
|               | L>`__.         |
+---------------+----------------+
| **BAD**       | A column type  |
|               | that contains  |
|               | only missing   |
|               | values.        |
+---------------+----------------+
| **Balance     | A parameter    |
| classes**     | that           |
|               | oversamples    |
|               | the minority   |
|               | classes to     |
|               | balance the    |
|               | distribution.  |
+---------------+----------------+
| **Beta        | A data.frame   |
| constraints** | or             |
|               | H2OParsedData  |
|               | object with    |
|               | the columns    |
|               | ["names",      |
|               | "lower\_bounds |
|               | ","upper\_boun |
|               | ds",           |
|               | "beta\_given"] |
|               | ,              |
|               | where each row |
|               | corresponds to |
|               | a predictor in |
|               | the GLM.       |
|               | "names"        |
|               | contains the   |
|               | predictor      |
|               | names,         |
|               | "lower\_bounds |
|               | "              |
|               | and            |
|               | "upper\_bounds |
|               | "              |
|               | are the lower  |
|               | and upper      |
|               | bounds of      |
|               | beta, and      |
|               | "beta\_given"  |
|               | is some        |
|               | supplied       |
|               | starting       |
|               | values for     |
|               | beta.          |
+---------------+----------------+
| **Binary**    | A variable     |
|               | with only two  |
|               | possible       |
|               | outcomes.      |
|               | Refer to       |
|               | `**binomial**  |
|               | <#Binomial>`__ |
|               | .              |
+---------------+----------------+
| \ **Binomial* | A variable     |
| *             | with the value |
|               | 0 or 1.        |
|               | Binomial       |
|               | variables      |
|               | assigned as 0  |
|               | indicate that  |
|               | an event       |
|               | hasn't         |
|               | occurred or    |
|               | that the       |
|               | observation    |
|               | lacks a        |
|               | feature, where |
|               | 1 indicates    |
|               | occurrence or  |
|               | instance of an |
|               | attribute.     |
+---------------+----------------+
| **Bins**      | Bins are       |
|               | linear-sized   |
|               | from the       |
|               | observed       |
|               | min-to-max for |
|               | the subset     |
|               | that is being  |
|               | split. Large   |
|               | bins are       |
|               | enforced for   |
|               | shallow tree   |
|               | depths. Based  |
|               | on the tree    |
|               | decisions, as  |
|               | the tree gets  |
|               | deeper, the    |
|               | bins are       |
|               | distributed    |
|               | symmetrically  |
|               | over the       |
|               | reduced range  |
|               | of each        |
|               | subset.        |
+---------------+----------------+
| \ **Categoric | A qualitative, |
| al**          | unordered      |
|               | variable (for  |
|               | example, *A*,  |
|               | *B*, *AB*, and |
|               | *O* would be   |
|               | values for the |
|               | category       |
|               | *blood type*); |
|               | synonym for    |
|               | `enumerator <# |
|               | Enum>`__       |
|               | or             |
|               | `factor <#Fact |
|               | or>`__.        |
|               | Stored as an   |
|               | ``int`` column |
|               | with a         |
|               | ``String``     |
|               | mapping in     |
|               | H2O; limited   |
|               | to 10 million  |
|               | unique strings |
|               | in H2O.        |
+---------------+----------------+
| \ **Classific | A model whose  |
| ation**       | goal is to     |
|               | predict the    |
|               | category for   |
|               | the            |
|               | `**response**  |
|               | <#Response>`__ |
|               | input.         |
+---------------+----------------+
| **Clip**      | In the H2O web |
|               | UI Flow, a     |
|               | clip is a      |
|               | single cell in |
|               | a flow         |
|               | containing an  |
|               | action that is |
|               | saved for      |
|               | later reuse.   |
+---------------+----------------+
| \ **Cloud**   | Synonym for    |
|               | cluster. Refer |
|               | to the         |
|               | definition for |
|               | `cluster <#Clu |
|               | ster>`__.      |
+---------------+----------------+
| \ **Cluster** | 1. A group of  |
|               | H2O nodes that |
|               | work together; |
|               | when a job is  |
|               | submitted to a |
|               | cluster, all   |
|               | the nodes in   |
|               | the cluster    |
|               | work on a      |
|               | portion of the |
|               | job. Synonym   |
|               | for            |
|               | `**cloud** <#C |
|               | loud>`__.      |
|               | 2. In          |
|               | statistics, a  |
|               | cluster is a   |
|               | group of       |
|               | observations   |
|               | from a data    |
|               | set identified |
|               | as similar     |
|               | according to a |
|               | particular     |
|               | clustering     |
|               | algorithm.     |
+---------------+----------------+
| **Confusion   | Table that     |
| matrix**      | depicts the    |
|               | performance of |
|               | the algorithm  |
|               | (using the     |
|               | false positive |
|               | rate, false    |
|               | negative, true |
|               | positive, and  |
|               | true negative  |
|               | rates).        |
+---------------+----------------+
| \ **Continuou | A variable     |
| s**           | that can take  |
|               | on all or      |
|               | nearly all     |
|               | values along   |
|               | an interval on |
|               | the real       |
|               | number line    |
|               | (for example,  |
|               | height or      |
|               | weight). The   |
|               | opposite of a  |
|               | `**discrete**  |
|               | <#Discrete>`__ |
|               | value, which   |
|               | can only take  |
|               | on certain     |
|               | numerical      |
|               | values (for    |
|               | example, the   |
|               | number of      |
|               | patients       |
|               | treated).      |
+---------------+----------------+
| **CSV file**  | CSV is an      |
|               | acronym for    |
|               | comma-separate |
|               | d              |
|               | value. A CSV   |
|               | file stores    |
|               | data in a      |
|               | plain text     |
|               | format.        |
+---------------+----------------+
| \ **Deep      | Uses a         |
| Learning**    | composition of |
|               | multiple       |
|               | non-linear     |
|               | transformation |
|               | s              |
|               | to model       |
|               | high-level     |
|               | abstractions   |
|               | in data.       |
+---------------+----------------+
| \ **Dependent | The            |
| variable**    | `**response**  |
|               | <#Response>`__ |
|               | column in H2O; |
|               | what you are   |
|               | trying to      |
|               | measure,       |
|               | observe, or    |
|               | predict. The   |
|               | opposite of an |
|               | `**independent |
|               | variable** <#I |
|               | ndependent>`__ |
|               | .              |
+---------------+----------------+
| **Data        | A distributed  |
| frame**       | representation |
|               | of a large     |
|               | dataset.       |
+---------------+----------------+
| **Destination | Automatically  |
| key**         | generated key  |
|               | for a model    |
|               | that allows    |
|               | recall of a    |
|               | specific model |
|               | later in       |
|               | analysis.      |
|               | Users can      |
|               | specify a      |
|               | different      |
|               | destination    |
|               | key than the   |
|               | key generated  |
|               | by H2O.        |
+---------------+----------------+
| **Deviance**  | Deviance is    |
|               | the difference |
|               | between an     |
|               | expected value |
|               | and an         |
|               | observed       |
|               | value. It      |
|               | plays a        |
|               | critical role  |
|               | in defining    |
|               | GLM models.    |
|               | For a more     |
|               | detailed       |
|               | discussion of  |
|               | deviance,      |
|               | please refer   |
|               | to the H2O     |
|               | Data Science   |
|               | documentation  |
|               | on GLM.        |
+---------------+----------------+
| \ **Distribut | Distributed    |
| ed            | key/value      |
| key/value     | store. Refer   |
| (DKV)**       | also to        |
|               | `**key/value   |
|               | store** <#KVst |
|               | ore>`__.       |
+---------------+----------------+
| \ **Discrete* | A variable     |
| *             | that can only  |
|               | take on        |
|               | certain        |
|               | numerical      |
|               | values (for    |
|               | example, the   |
|               | number of      |
|               | patients       |
|               | treated). The  |
|               | opposite of a  |
|               | `**continuous* |
|               | * <#Continuous |
|               | >`__           |
|               | variable.      |
+---------------+----------------+
| \ **Enumerato | A data type    |
| r/enum**      | where the      |
|               | value is one   |
|               | of a defined   |
|               | set of named   |
|               | values known   |
|               | as "elements", |
|               | "members", or  |
|               | "enumerators." |
|               | For example,   |
|               | *cat*, *dog*,  |
|               | & *mouse* are  |
|               | enumerators of |
|               | the enumerated |
|               | type *animal*. |
+---------------+----------------+
| \ **Epoch**   | A round or     |
|               | iteration of   |
|               | model training |
|               | or testing.    |
|               | Refer also to  |
|               | `**iteration** |
|               |  <#Iteration>` |
|               | __.            |
+---------------+----------------+
| \ **Factor**  | A data type    |
|               | where the      |
|               | value is one   |
|               | of a defined   |
|               | set of         |
|               | categories.    |
|               | Refer to       |
|               | `**Enum** <#En |
|               | um>`__         |
|               | and            |
|               | `**Categorical |
|               | ** <#Categoric |
|               | al>`__.        |
+---------------+----------------+
| **Family**    | The            |
|               | distribution   |
|               | options        |
|               | available for  |
|               | predictive     |
|               | modeling in    |
|               | GLM.           |
+---------------+----------------+
| **Feature**   | Synonym for    |
|               | attribute,     |
|               | predictor, or  |
|               | independent    |
|               | variable.      |
|               | Usually refers |
|               | to the data    |
|               | observed on    |
|               | features given |
|               | in the columns |
|               | of a data set. |
+---------------+----------------+
| **Feed-forwar | Associates     |
| d**           | input with     |
|               | output for     |
|               | pattern        |
|               | recognition.   |
+---------------+----------------+
| **Flatfile**  | A basic text   |
|               | file           |
|               | containing     |
|               | multiple IP    |
|               | addresses (one |
|               | per line) used |
|               | by H2O to      |
|               | configure a    |
|               | cluster.       |
+---------------+----------------+
| **Flow**      | Refers to the  |
|               | series of      |
|               | cell-based     |
|               | actions        |
|               | created in     |
|               | H2O's web UI   |
|               | or the web UI  |
|               | itself.        |
+---------------+----------------+
| **Gzipped     | Gzip is a type |
| (gz) file**   | of file        |
|               | compression    |
|               | commonly used  |
|               | for H2O file   |
|               | dependencies.  |
+---------------+----------------+
| **HEX         | Records made   |
| format**      | up of          |
|               | hexadecimal    |
|               | numbers        |
|               | representing   |
|               | machine        |
|               | language code  |
|               | or constant    |
|               | data. In H2O,  |
|               | data must be   |
|               | parsed into    |
|               | .hex format    |
|               | before you can |
|               | perform        |
|               | operations on  |
|               | it.            |
+---------------+----------------+
| \ **Independe | The factors    |
| nt            | can be         |
| variable**    | manipulated or |
|               | controlled     |
|               | (also known as |
|               | predictors).   |
|               | The opposite   |
|               | of a           |
|               | `**dependent   |
|               | variable** <#D |
|               | ependent>`__.  |
+---------------+----------------+
| **Hit ratio** | (Multinomial   |
|               | only) The      |
|               | number of      |
|               | times the      |
|               | prediction was |
|               | correct out of |
|               | the total      |
|               | number of      |
|               | predictions.   |
+---------------+----------------+
| **Instance**  | Occurs each    |
|               | time H2O is    |
|               | started. This  |
|               | process builds |
|               | a cluster of   |
|               | nodes (even if |
|               | it is only a   |
|               | one-node       |
|               | cluster on a   |
|               | local          |
|               | machine). The  |
|               | instance       |
|               | begins when    |
|               | the cluster is |
|               | formed and     |
|               | ends when the  |
|               | program is     |
|               | closed.        |
+---------------+----------------+
| **Integer**   | A whole number |
|               | (can be        |
|               | negative but   |
|               | cannot be a    |
|               | fraction). Can |
|               | be represented |
|               | in H2O as an   |
|               | ``int``, which |
|               | is not a type  |
|               | but a property |
|               | of the data.   |
+---------------+----------------+
| \ **Iteration | A round or     |
| **            | instance of    |
|               | model testing  |
|               | or training.   |
|               | Also known as  |
|               | an             |
|               | `**epoch** <#E |
|               | poch>`__.      |
+---------------+----------------+
| **Job**       | A task         |
|               | performed by   |
|               | H2O. For       |
|               | example,       |
|               | reading a data |
|               | file, parsing  |
|               | a data file,   |
|               | or building a  |
|               | model. In the  |
|               | browser-based  |
|               | GUI of H2O,    |
|               | each job is    |
|               | listed in the  |
|               | **Admin** menu |
|               | under          |
|               | **Jobs**.      |
+---------------+----------------+
| **JVM**       | Java virtual   |
|               | machine; used  |
|               | to run H2O.    |
+---------------+----------------+
| **Key**       | The .hex key   |
|               | generated when |
|               | data are       |
|               | parsed into    |
|               | H2O. In the    |
|               | web-based GUI, |
|               | **key** is an  |
|               | input on each  |
|               | page where     |
|               | users define   |
|               | models and any |
|               | page where     |
|               | users validate |
|               | models on a    |
|               | new data set   |
|               | or use a model |
|               | to generate    |
|               | predictions.   |
+---------------+----------------+
| **Key/value   | A type of data |
| pair**        | that           |
|               | associates a   |
|               | particular key |
|               | index to a     |
|               | certain datum. |
+---------------+----------------+
| \ **Key/value | A tool that    |
| store**       | allows storage |
|               | of schema-less |
|               | data. Data     |
|               | usually        |
|               | consists of a  |
|               | string that    |
|               | represents the |
|               | key, and the   |
|               | data itself,   |
|               | which is the   |
|               | value. Refer   |
|               | also to        |
|               | `**distributed |
|               | key/value** <# |
|               | DistKV>`__.    |
+---------------+----------------+
| **L1          | A              |
| regularizatio | regularization |
| n**           | method that    |
|               | constrains the |
|               | absolute value |
|               | of the weights |
|               | and has the    |
|               | net effect of  |
|               | dropping some  |
|               | values         |
|               | (setting them  |
|               | to zero) from  |
|               | a model to     |
|               | reduce         |
|               | complexity and |
|               | avoid          |
|               | overfitting.   |
+---------------+----------------+
| **L2          | A              |
| regularizatio | regularization |
| n**           | method that    |
|               | constrains the |
|               | sum of the     |
|               | squared        |
|               | weights. This  |
|               | method         |
|               | introduces     |
|               | bias into      |
|               | parameter      |
|               | estimates but  |
|               | frequently     |
|               | produces       |
|               | substantial    |
|               | gains in       |
|               | modeling as    |
|               | estimate       |
|               | variance is    |
|               | reduced.       |
+---------------+----------------+
| **Link        | A user-defined |
| function**    | option in GLM. |
+---------------+----------------+
| **Loss        | The function   |
| function**    | minimized in   |
|               | order to       |
|               | achieve a      |
|               | desired        |
|               | estimator;     |
|               | synonymous to  |
|               | objective      |
|               | function and   |
|               | criterion      |
|               | function. For  |
|               | example,       |
|               | linear         |
|               | regression     |
|               | defines the    |
|               | set of best    |
|               | parameter      |
|               | estimates as   |
|               | the set of     |
|               | estimates that |
|               | produces the   |
|               | minimum of the |
|               | sum of the     |
|               | squared        |
|               | errors. Errors |
|               | are the        |
|               | difference     |
|               | between the    |
|               | predicted      |
|               | value and the  |
|               | observed       |
|               | value.         |
+---------------+----------------+
| **MSE**       | Mean squared   |
|               | error;         |
|               | measures the   |
|               | average of the |
|               | squares of the |
|               | error rate     |
|               | (the           |
|               | difference     |
|               | between the    |
|               | predictors and |
|               | what was       |
|               | predicted).    |
+---------------+----------------+
| **Multinomial | A variable     |
| **            | where the      |
|               | value can be   |
|               | one of more    |
|               | than two       |
|               | possible       |
|               | outcomes (for  |
|               | example, blood |
|               | type).         |
+---------------+----------------+
| **N-folds**   | User-defined   |
|               | number of      |
|               | cross          |
|               | validation     |
|               | models         |
|               | generated by   |
|               | H2O.           |
+---------------+----------------+
| **Node**      | In distributed |
|               | computing      |
|               | systems, nodes |
|               | include        |
|               | clients,server |
|               | s,             |
|               | or peers. In   |
|               | statistics, a  |
|               | node is a      |
|               | decision or    |
|               | terminal point |
|               | in a           |
|               | classification |
|               | tree.          |
+---------------+----------------+
| **Numeric**   | A column type  |
|               | containing     |
|               | real numbers,  |
|               | small          |
|               | integers, or   |
|               | booleans.      |
+---------------+----------------+
| **Offset**    | A parameter    |
|               | that           |
|               | compensates    |
|               | for            |
|               | differences in |
|               | units of       |
|               | observation    |
|               | (for example,  |
|               | different      |
|               | populations or |
|               | geographic     |
|               | sizes) to make |
|               | sure outcome   |
|               | is             |
|               | proportional.  |
+---------------+----------------+
| **Outline**   | In H2O's web   |
|               | UI Flow, a     |
|               | brief summary  |
|               | of the actions |
|               | contained in   |
|               | the cells.     |
+---------------+----------------+
| **Parse**     | Analysis of a  |
|               | string of      |
|               | symbols or     |
|               | datum that     |
|               | results in the |
|               | conversion of  |
|               | a set of       |
|               | information    |
|               | from a         |
|               | person-readabl |
|               | e              |
|               | format to a    |
|               | machine-readab |
|               | le             |
|               | format.        |
+---------------+----------------+
| **POJO**      | Plain Old Java |
|               | Object; a way  |
|               | to export a    |
|               | model built in |
|               | H2O and        |
|               | implement it   |
|               | in a Java      |
|               | application.   |
+---------------+----------------+
| \ **Regressio | A model where  |
| n**           | the input is   |
|               | numerical and  |
|               | the output is  |
|               | a prediction   |
|               | of numerical   |
|               | values. Also   |
|               | known as       |
|               | "quantitative" |
|               | ;              |
|               | the opposite   |
|               | of a           |
|               | `**classificat |
|               | ion** <#Classi |
|               | fication>`__   |
|               | model.         |
+---------------+----------------+
| \ **Response  | Method of      |
| column**      | selecting the  |
|               | `**dependent** |
|               |  <#Dependent>` |
|               | __             |
|               | variable in    |
|               | H2O.           |
+---------------+----------------+
| **Real**      | A fractional   |
|               | number.        |
+---------------+----------------+
| **ROC Curve** | Graph          |
|               | representing   |
|               | the ratio to   |
|               | true positives |
|               | to false       |
|               | positives.     |
+---------------+----------------+
| **Scoring     | Represents the |
| history**     | error rate of  |
|               | the model as   |
|               | it is built.   |
+---------------+----------------+
| **Seed**      | A starting     |
|               | point for      |
|               | randomization. |
|               | Seed           |
|               | specification  |
|               | is used when   |
|               | machine        |
|               | learning       |
|               | models have a  |
|               | random         |
|               | component; it  |
|               | allows users   |
|               | to recreate    |
|               | the exact      |
|               | "random"       |
|               | conditions     |
|               | used in a      |
|               | model at a     |
|               | later time.    |
+---------------+----------------+
| **Separator** | What separates |
|               | the entries in |
|               | the dataset;   |
|               | usually a      |
|               | comma,         |
|               | semicolon,     |
|               | etc.           |
+---------------+----------------+
| **Sparse**    | A dataset      |
|               | where many of  |
|               | the rows       |
|               | contain blank  |
|               | values or "NA" |
|               | instead of     |
|               | data.          |
+---------------+----------------+
| **Standard    | The standard   |
| deviation**   | deviation of   |
|               | the data in    |
|               | the column,    |
|               | defined as the |
|               | square root of |
|               | the sum of the |
|               | deviance of    |
|               | observed       |
|               | values from    |
|               | the mean       |
|               | divided by the |
|               | number of      |
|               | elements in    |
|               | the column     |
|               | minus one.     |
|               | Abbreviated    |
|               | *sd*.          |
+---------------+----------------+
| **Standardiza | Transformation |
| tion**        | of a variable  |
|               | so that it is  |
|               | mean-centered  |
|               | at 0 and       |
|               | scaled by the  |
|               | standard       |
|               | deviation;     |
|               | helps prevent  |
|               | precision      |
|               | problems.      |
+---------------+----------------+
| **String**    | Refers to data |
|               | where each     |
|               | entry is       |
|               | typically      |
|               | unique (for    |
|               | example, a     |
|               | dataset        |
|               | containing     |
|               | people's names |
|               | and            |
|               | addresses).    |
+---------------+----------------+
| **Supervised  | Model type     |
| learning**    | where the      |
|               | input is       |
|               | labeled so     |
|               | that the       |
|               | algorithm can  |
|               | identify it    |
|               | and learn from |
|               | it.            |
+---------------+----------------+
| **Time**      | Data type      |
|               | supported by   |
|               | H2O;           |
|               | represented as |
|               | "milliseconds- |
|               | since-the-Unix |
|               | -Epoch";       |
|               | stored         |
|               | internally as  |
|               | a 64-bit       |
|               | integer in a   |
|               | standard       |
|               | ``int``        |
|               | column. Used   |
|               | directly by    |
|               | the Cox        |
|               | Proportional   |
|               | Hazards model  |
|               | but also used  |
|               | to build other |
|               | features.      |
+---------------+----------------+
| **Training    | The dataset    |
| frame**       | used to build  |
|               | the model.     |
+---------------+----------------+
| **Unsupervise | Model type     |
| d             | where the      |
| learning**    | input is not   |
|               | labeled.       |
+---------------+----------------+
| **UUID**      | A dense        |
|               | representation |
|               | of universally |
|               | unique         |
|               | identifiers    |
|               | (UUIDs) used   |
|               | to label and   |
|               | group events;  |
|               | stored as a    |
|               | 128-bit        |
|               | numeric value. |
+---------------+----------------+
| **Validation* | An analysis of |
| *             | how well the   |
|               | model fits.    |
+---------------+----------------+
| **Validation  | The dataset    |
| frame**       | used to        |
|               | evaluate the   |
|               | accuracy of    |
|               | the model.     |
+---------------+----------------+
| **Variable    | Represents the |
| importance**  | statistical    |
|               | significance   |
|               | of each        |
|               | variable in    |
|               | the data in    |
|               | terms of its   |
|               | affect on the  |
|               | model.         |
+---------------+----------------+
| **Weights**   | A parameter    |
|               | that specifies |
|               | certain        |
|               | outcomes as    |
|               | more           |
|               | significant    |
|               | (for example,  |
|               | if you are     |
|               | trying to      |
|               | identify       |
|               | incidence of   |
|               | disease, one   |
|               | "positive"     |
|               | result can be  |
|               | more           |
|               | meaningful     |
|               | than 50        |
|               | "negative"     |
|               | responses).    |
|               | Higher values  |
|               | indicate more  |
|               | importance.    |
+---------------+----------------+
| **XLS file**  | A Microsoft    |
|               | Excel          |
|               | 2003-2007      |
|               | spreadsheet    |
|               | file format.   |
+---------------+----------------+
| **Y**         | Dependent      |
|               | variable used  |
|               | in GLM; a      |
|               | user-defined   |
|               | input selected |
|               | from the set   |
|               | of variables   |
|               | present in the |
|               | user's data.   |
+---------------+----------------+
| **YARN**      | Yet Another    |
|               | Resource       |
|               | Manager; used  |
|               | to manage H2O  |
|               | on a Hadoop    |
|               | cluster.       |
+---------------+----------------+
