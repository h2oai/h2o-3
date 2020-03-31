package hex.grid;

import hex.Model;
import hex.ModelParametersBuilderFactory;
import hex.ScoreKeeper;
import hex.ScoringInfo;
import hex.grid.HyperSpaceSearchCriteria.SequentialSearchCriteria;
import hex.grid.HyperSpaceSearchCriteria.StoppingCriteria;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SequentialWalker<MP extends Model.Parameters> implements HyperSpaceWalker<MP, SequentialSearchCriteria> {

    private final MP _params;
    private final Object[][] _hyperParams;
    private final String[] _hyperParamNames;
    private final ModelParametersBuilderFactory _paramsBuilderFactory;
    private final SequentialSearchCriteria _searchCriteria;

    public SequentialWalker(MP params,
                            Object[][] hyperParams,
                            ModelParametersBuilderFactory<MP> paramsBuilderFactory,
                            SequentialSearchCriteria searchCriteria) {
        assert hyperParams.length > 1;
        assert Stream.of(hyperParams[0]).allMatch(c -> c instanceof String) : "first row of hyperParams must contains hyper-parameter names";

        _params = params;
        _hyperParamNames = new String[hyperParams[0].length];
        System.arraycopy(hyperParams[0], 0, _hyperParamNames, 0, _hyperParamNames.length);
        _hyperParams = Arrays.copyOfRange(hyperParams, 1, hyperParams.length);
        _paramsBuilderFactory = paramsBuilderFactory;
        _searchCriteria = searchCriteria;
    }

    @Override
    public SequentialSearchCriteria search_criteria() {
        return _searchCriteria;
    }

    @Override
    public String[] getHyperParamNames() {
        return _hyperParamNames;
    }

    @Override
    public long getMaxHyperSpaceSize() {
        return _hyperParams.length;
    }

    @Override
    public MP getParams() {
        return _params;
    }

    @Override
    public ModelParametersBuilderFactory<MP> getParametersBuilderFactory() {
        return _paramsBuilderFactory;
    }

    @Override
    public boolean stopEarly(Model model, ScoringInfo[] sk) {
        StoppingCriteria stoppingCriteria = search_criteria().stoppingCriteria();
        return ScoreKeeper.stopEarly(
                ScoringInfo.scoreKeepers(sk),
                stoppingCriteria.getStoppingRounds(),
                ScoreKeeper.ProblemType.forSupervised(model._output.isClassifier()),
                stoppingCriteria.getStoppingMetric(),
                stoppingCriteria.getStoppingTolerance(),
                "grid's best",
                true
        );
    }

    private MP getModelParams(MP params, Object[] hyperParams) {
        ModelParametersBuilderFactory.ModelParametersBuilder<MP> paramsBuilder = _paramsBuilderFactory.get(params.clone());
        for (int i = 0; i < _hyperParamNames.length; i++) {
            String paramName = _hyperParamNames[i];
            Object paramValue = hyperParams[i];
            if (paramValue != null)
                paramsBuilder.set(paramName, paramValue);
        }
        return paramsBuilder.build();
    }

    @Override
    public HyperSpaceIterator<MP> iterator() {
        return new HyperSpaceIterator<MP>() {

            private int _index = -1;

            @Override
            public MP nextModelParameters(Model previousModel) {
                return getModelParams(_params, _hyperParams[++_index]);
            }

            @Override
            public boolean hasNext(Model previousModel) {
                return _index+1 < getMaxHyperSpaceSize();
            }

            @Override
            public void onModelFailure(Model failedModel, Consumer<Object[]> withFailedModelHyperParams) {
                withFailedModelHyperParams.accept(_hyperParams[_index]); //TODO: identify index of failedModel
            }
        };
    }
}
