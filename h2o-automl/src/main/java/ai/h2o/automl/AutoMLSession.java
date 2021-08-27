package ai.h2o.automl;

import water.DKV;
import water.Futures;
import water.Key;
import water.Keyed;
import water.nbhm.NonBlockingHashMap;
import water.util.IcedHashMap;
import water.util.IcedHashSet;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoMLSession extends Keyed<AutoMLSession> {

    private static Key<AutoMLSession> makeKey(String projectName) {
        return Key.make("AutoMLSession_"+projectName);
    }

    public static AutoMLSession getInstance(String projectName) {
        AutoMLSession session = DKV.getGet(makeKey(projectName));
        if (session == null) {
            session = new AutoMLSession(projectName);
            DKV.put(session);
        }
        return session;
    }

    private final String _projectName;
    private final ModelingStepsRegistry _modelingStepsRegistry;
    private IcedHashSet<Key<Keyed>> _resumableKeys = new IcedHashSet();
    private IcedHashMap<Key, String[]> _keySources = new IcedHashMap<>();
    private NonBlockingHashMap<String, AtomicInteger> _modelCounters = new NonBlockingHashMap<>();
    
    private transient NonBlockingHashMap<String, ModelingSteps> _availableStepsByProviderName = new NonBlockingHashMap<>();
    private transient AutoML _aml;
    
    AutoMLSession(String projectName) {
        super(makeKey(projectName));
        _projectName = projectName;
        _modelingStepsRegistry = new ModelingStepsRegistry();
    }
    
    public ModelingStepsRegistry getModelingStepsRegistry() {
        return _modelingStepsRegistry;
    }
    
    void attach(AutoML aml) {
        assert _projectName.equals(aml._key.toString()): "AutoMLSession can only be attached to an AutoML instance from project '"+_projectName+"', but got: "+aml._key;
        if (_aml == null) _aml = aml;
    }
    
    public ModelingStep getModelingStep(Key key) {
        if (!_keySources.containsKey(key)) return null;
        String[] identifiers = _keySources.get(key);
        assert identifiers.length > 1;
        return getModelingStep(identifiers[0], identifiers[1]);
    }

    public ModelingStep getModelingStep(String providerName, String id) {
        ModelingSteps steps = getModelingSteps(providerName);
        return steps == null ? null : steps.getStep(id).orElse(null);
    }

    ModelingSteps getModelingSteps(String providerName) {
        if (!_availableStepsByProviderName.containsKey(providerName)) {
            ModelingStepsProvider provider = _modelingStepsRegistry.stepsByName.get(providerName);
            if (provider == null) {
//        eventLog().warn(Stage.ModelTraining, "Missing provider for modeling steps '"+providerName+"'");
//        return null;
                throw new IllegalArgumentException("Missing provider for modeling steps '"+providerName+"'");
            }
            ModelingSteps steps = provider.newInstance(_aml);
            _availableStepsByProviderName.put(providerName, steps);
        }
        return _availableStepsByProviderName.get(providerName);
    }

    public void registerKeySource(Key key, ModelingStep step) {
        if (key != null && !_keySources.containsKey(key)) _keySources.put(key, new String[]{step.getProvider(), step.getId()});
    }

    public void addResumableKey(Key key) {
        _resumableKeys.add(key);
    }

    public Key[] getResumableKeys(String providerName, String id) {
        ModelingStep step = getModelingStep(providerName, id);
        if (step == null) return new Key[0];
        return _resumableKeys.stream()
                .filter(k -> step.equals(getModelingStep(k)))
                .toArray(Key[]::new);
    }

    public int nextModelCounter(String algoName, String type) {
        String key = algoName+"_"+type;
        if (!_modelCounters.containsKey(key)) {
            synchronized (_modelCounters) {
                if (!_modelCounters.containsKey(key))
                    _modelCounters.put(key, new AtomicInteger(0));
            }
        }
        return _modelCounters.get(key).incrementAndGet();
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        _resumableKeys.clear();
        _keySources.clear();
        _availableStepsByProviderName.clear();
        return super.remove_impl(fs, cascade);
    }
}
