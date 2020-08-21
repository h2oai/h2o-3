package hex;

import water.Key;

public interface ModelContainer<M extends Model> {
    Key<M>[] getModelKeys();

    M[] getModels();

    int getModelCount();
}
