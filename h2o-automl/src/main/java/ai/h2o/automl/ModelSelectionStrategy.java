package ai.h2o.automl;

import hex.Model;
import water.Key;

@FunctionalInterface
public interface ModelSelectionStrategy<M extends Model>{

    class Selection<M extends Model> {
        final Key<M>[] _add;  //models that should be added to the original population
        final Key<M>[] _remove; //models that should be removed from the original population

        public Selection(Key<M>[] add, Key<M>[] remove) {
            _add = add;
            _remove = remove;
        }
    }

    Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels);
}
