package ai.h2o.automl;

import hex.Model;
import hex.ModelContainer;
import water.*;
import water.api.schemas3.KeyV3;
import water.automl.api.schemas3.SchemaExtensions;
import water.util.ArrayUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class Models<M extends Model> extends Lockable<Models<M>> implements ModelContainer<M> {

    private final int _type_id;
    private final Job _job;
    private Key<M>[] _modelKeys = new Key[0];

    public Models(Key<Models<M>> key, Class<M> clz) {
        this(key, clz, null);
    }

    public Models(Key<Models<M>> key, Class<M> clz, Job job) {
        super(key);
        _type_id = (clz != null && !Modifier.isAbstract(clz.getModifiers())) ? TypeMap.getIcedId(clz.getName()) : -1;
        _job = job;
    }

    @Override
    public Key<M>[] getModelKeys() {
        return _modelKeys.clone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public M[] getModels() {
        Arrays.stream(_modelKeys).forEach(DKV::prefetch);
        Class<M> clz = (Class<M>)(_type_id >= 0 ? TypeMap.theFreezable(_type_id).getClass(): Model.class);
        return Arrays.stream(_modelKeys)
                .map(k -> k == null ? null : k.get())
                .toArray(l -> (M[])Array.newInstance(clz, l));
    }

    @Override
    public int getModelCount() {
        return _modelKeys.length;
    }

    public void addModel(Key<M> key) {
        addModels(new Key[]{key});
    }

    public void addModels(Key<M>[] keys) {
       write_lock(_job);
       _modelKeys = ArrayUtils.append(_modelKeys, keys);
       update(_job);
       unlock(_job);
    }

    @Override
    protected Futures remove_impl(final Futures fs, boolean cascade) {
        if (cascade) {
            for (Key<M> k : _modelKeys)
                Keyed.remove(k, fs, true);
        }
        _modelKeys = new Key[0];
        return super.remove_impl(fs, cascade);
    }

    @Override
    public Class<SchemaExtensions.ModelsKeyV3> makeSchema() {
        return SchemaExtensions.ModelsKeyV3.class;
    }
}
