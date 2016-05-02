package ai.h2o.automl;


public class AutoMLTest2 {

  /**
   * AutoML Strategy 1:
   *    1. Generate 100 features at a time and store the top 10 from a 5-tree 5-deep GBM
   *        A. 10M features -> 10K features. Cycle thru again and get the top 100 features
   *        B. Aggregates are part of this story too:
   *            i. generate some number of features N
   *           ii. generate some number of aggregates
   *          iii. prune these generated features into something reasonable
   *            * Note that for aggregates, we have 3*c!*n of them
   *              where
   *                c is the number of categorical columns
   *                n is the number of numerical columns
   *                3 is (sum,mean,var)
   *              the number of keys to use for grouping data (c!) is based on a heuristic
   *              that strives for a small number of groups (<5M groups is the heuristic and
   *              is based on the fact that NBHM perf degrades with a few million entries).
   *
   *    2. Run KMeans on the numeric columns with clusters of 3,4, and 5. Use these as additional
   *       ways to form aggregates on the data.
   *
   *
   *
   *  Building models:
   *      1. Build a 1 tree GBM, then build TIME_BUDGET / time_for_one_tree trees
   *      2. shrinkage = 1 / (1.5*ntree)
   *      3. depth = 6
   *
   */


}
