package hex.grid.hyperspace;

import hex.Model;
import hex.ModelParametersBuilderFactory;
import hex.ScoreKeeper;
import hex.ScoringInfo;

import java.util.*;

import static java.lang.StrictMath.min;

public class RandomDiscreteValueWalker<MP extends Model.Parameters>
        extends HyperSpaceWalker.BaseWalker<MP, HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria> {

  Random random;

  /**
   * All visited hyper params permutations, including the current one.
   */
  private List<int[]> _visitedPermutations = new ArrayList<>();
  private Set<Integer> _visitedPermutationHashes = new LinkedHashSet<>(); // for fast dupe lookup

  public RandomDiscreteValueWalker(MP params,
                                   Map<String, Object[]> hyperParams,
                                   ModelParametersBuilderFactory<MP> paramsBuilderFactory,
                                   HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria search_criteria) {
    super(params, hyperParams, paramsBuilderFactory, search_criteria);

    if (-1 == search_criteria.seed())
      random = new Random();                       // true random
    else
      random = new Random(search_criteria.seed()); // seeded repeatable pseudorandom
  }

  /**
   * Based on the last model, the given array of ScoringInfo, and our stopping criteria should we stop early?
   */
  @Override
  public boolean stopEarly(Model model, ScoringInfo[] sk) {
    return ScoreKeeper.stopEarly(ScoringInfo.scoreKeepers(sk),
            search_criteria().stopping_rounds(),
            ScoreKeeper.ProblemType.forSupervised(model._output.isClassifier()),
            search_criteria().stopping_metric(),
            search_criteria().stopping_tolerance(), "grid's best", true);
  }

  @Override
  public HyperSpaceIterator<MP> iterator() {
    return new HyperSpaceIterator<MP>() {
      /** Current hyper params permutation. */
      private int[] _currentHyperparamIndices = null;

      /** One-based count of the permutations we've visited, primarily used as an index into _visitedHyperparamIndices. */
      private int _currentPermutationNum = 0;

      /** Start time of this grid */
      private long _start_time = System.currentTimeMillis();

      @Override
      public List<MP> initialModelParameters(int numParams) {
        List<MP> parameters = new ArrayList<>(numParams);

        for (int i = 0; i < numParams; i++) {
          parameters.add(nextModelParameters(Optional.empty()));
        }

        return parameters;
      }

      // TODO: override into a common subclass:
      @Override
      public MP nextModelParameters(Optional<Model> previousModel) {
        // NOTE: nextModel checks _visitedHyperparamIndices and does not return a duplicate set of indices.
        // NOTE: in RandomDiscreteValueWalker nextModelIndices() returns a new array each time, rather than
        // mutating the last one.
        _currentHyperparamIndices = nextModelIndices();

        if (_currentHyperparamIndices != null) {
          _visitedPermutations.add(_currentHyperparamIndices);
          _visitedPermutationHashes.add(integerHash(_currentHyperparamIndices));
          _currentPermutationNum++; // NOTE: 1-based counting

          // Fill array of hyper-values
          Object[] hypers = hypers(_currentHyperparamIndices, new Object[_hyperParamNames.length]);
          // Get clone of parameters
          MP commonModelParams = (MP) _params.clone();
          // Fill model parameters
          MP params = getModelParams(commonModelParams, hypers);

          // add max_runtime_secs in search criteria into params if applicable
          if (_search_criteria != null && _search_criteria.strategy() == HyperSpaceSearchCriteria.Strategy.RandomDiscrete) {
            // ToDo: model seed setting will be different for parallel model building.
            // ToDo: This implementation only works for sequential model building.
            if (_set_model_seed_from_search_seed) {
              // set model seed = search_criteria.seed+(0, 1, 2,..., model number)
              params._seed = _search_criteria.seed() + (model_number++);
            }

            // set max_runtime_secs
            double timeleft = this.time_remaining_secs();
            if (timeleft > 0) {
              if (params._max_runtime_secs > 0) {
                params._max_runtime_secs = min(params._max_runtime_secs, timeleft);
              } else {
                params._max_runtime_secs = timeleft;
              }
            }
          }
          return params;
        } else {
          throw new NoSuchElementException("No more elements to explore in hyper-space!");
        }
      }

      @Override
      public boolean hasNext(Optional<Model> previousModel) {
        // Note: we compare _currentPermutationNum to max_models, because it counts successfully created models, but
        // we compare _visitedPermutationHashes.size() to _maxHyperSpaceSize because we want to stop when we have attempted each combo.
        //
        // _currentPermutationNum is 1-based
        return (_visitedPermutationHashes.size() < _maxHyperSpaceSize &&
                (search_criteria().max_models() == 0 || _currentPermutationNum < search_criteria().max_models())
        );
      }

      @Override
      public void reset() {
        _start_time = System.currentTimeMillis();
        _currentPermutationNum = 0;
        _currentHyperparamIndices = null;
        _visitedPermutations.clear();
        _visitedPermutationHashes.clear();
      }

      public double max_runtime_secs() {
        return search_criteria().max_runtime_secs();
      }

      public int max_models() {
        return search_criteria().max_models();
      }

      @Override
      public double time_remaining_secs() {
        return search_criteria().max_runtime_secs() - (System.currentTimeMillis() - _start_time) / 1000.0;
      }

      @Override
      public void modelFailed(Model failedModel) {
        // Leave _visitedPermutations, _visitedPermutationHashes and _currentHyperparamIndices alone
        // so we don't revisit bad parameters. Note that if a model build fails for other reasons we
        // won't retry.
        _currentPermutationNum--;
      }

      @Override
      public Object[] getCurrentRawParameters() {
        Object[] hyperValues = new Object[_hyperParamNames.length];
        return hypers(_currentHyperparamIndices, hyperValues);
      }
    }; // anonymous HyperSpaceIterator class
  } // iterator()

  /**
   * Random iteration over the hyper-parameter space.  Does not repeat
   * previously-visited combinations.  Returns NULL when we've hit the stopping
   * criteria.
   */
  private int[] nextModelIndices() {
    int[] hyperparamIndices = new int[_hyperParamNames.length];

    do {
      // generate random indices
      for (int i = 0; i < _hyperParamNames.length; i++) {
        hyperparamIndices[i] = random.nextInt(_hyperParams.get(_hyperParamNames[i]).length);
      }
      // check for aliases and loop if we've visited this combo before
    } while (_visitedPermutationHashes.contains(integerHash(hyperparamIndices)));

    return hyperparamIndices;
  } // nextModel
}
