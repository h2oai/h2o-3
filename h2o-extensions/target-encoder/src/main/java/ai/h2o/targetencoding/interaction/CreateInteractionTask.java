package ai.h2o.targetencoding.interaction;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class CreateInteractionTask extends MRTask<CreateInteractionTask> {
    final InteractionsEncoder _encoder;
    final long[] _interactionDomain; // sorted by construction (see createInteractionColumn), or null

    private transient Map<Long, Integer> _interactionValueToCategoricalValue;

    public CreateInteractionTask(InteractionsEncoder encoder, String[] interactionDomain) {
        _encoder = encoder;
        _interactionDomain = interactionDomain==null ? null : Arrays.stream(interactionDomain).mapToLong(Long::parseLong).toArray();
    }

    @Override
    protected void setupLocal() {
        if (_interactionDomain != null) {
            _interactionValueToCategoricalValue = new HashMap<>();
            for (int i = 0; i < _interactionDomain.length; i++) {
                _interactionValueToCategoricalValue.put(_interactionDomain[i], i);
            }
        }
    }

    @Override
    public void map(Chunk[] cs, NewChunk nc) {
        for (int row = 0; row < cs[0].len(); row++) {
            int[] interactingValues = new int[cs.length];
            for (int i = 0; i < cs.length; i++) {
                interactingValues[i] = cs[i].isNA(row) ? -1:(int) cs[i].at8(row);
            }
            long val = _encoder.encode(interactingValues);
            if (val < 0) {
                nc.addNA();
            } else if (_interactionDomain==null) {
                nc.addNum(val);
            } else {
                int catVal = _interactionValueToCategoricalValue.getOrDefault(val, -1);
                if (catVal < 0)
                    nc.addNA();
                else
                    nc.addCategorical(catVal);
            }
        }
    }
}
