package ai.h2o.automl;

import water.util.IcedHashMap;

import java.util.Collections;
import java.util.Map;

import static ai.h2o.automl.StepResultState.ResultStatus.*;

final class StepResultState {

    enum ResultStatus {
        skipped,
        cancelled,
        failed,
        success,
    }

    enum Resolution {
        sameAsMain,  // resolves to the same state as the main step (ignoring other sub-step states).
        optimistic,  // success if any success, otherwise cancelled if any cancelled, otherwise failed if any failure, otherwise skipped.
        pessimistic, // failed if any failure, otherwise cancelled if any cancelled, otherwise success it any success, otherwise skipped.
    }

    private final String _id;
    private final IcedHashMap<String, StepResultState> _subStates = new IcedHashMap<>();
    private ResultStatus _status;
    private Throwable _error;

    StepResultState(String id) {
        this(id, (ResultStatus) null);
    }

    StepResultState(String id, ResultStatus status) {
        this(id, status, null);
    }

    StepResultState(String id, Throwable error) {
        this(id, failed, error);
    }
    
    private StepResultState(String id, ResultStatus status, Throwable error) {
        _id = id;
        _status = status;
        _error = error;
    }

    void setStatus(ResultStatus status) {
        assert _status == null;
        _status = status;
    }

    void setStatus(Throwable error) {
        setStatus(failed);
        _error = error;
    }

    void addState(StepResultState state) {
        _subStates.put(state.id(), state);
    }

    boolean is(ResultStatus status) {
        return _status==status;
    }

    String id() {
        return _id;
    }

    ResultStatus status() {
        return _status;
    }

    Throwable error() {
        return _error;
    }

    StepResultState subState(String id) {
        return _subStates.get(id);
    }

    Map<String, StepResultState> subStates() {
        return Collections.unmodifiableMap(_subStates);
    }

    void resolveState(Resolution strategy) {
        if (_status != null) return;
        if (_subStates.size() == 0) {
            setStatus(skipped);
        } else if (_subStates.size() == 1 && _subStates.containsKey(id())) {
            StepResultState state = subState(id());
            _status = state.status();
            _error = state.error();
            _subStates.clear();
            _subStates.putAll(state.subStates());
        } else {
            switch (strategy) {
                case sameAsMain:
                    StepResultState state = subState(id());
                    if (state != null) {
                        _status = state.status();
                        _error = state.error();
                    } else {
                        _status = cancelled;
                    }
                    break;
                case optimistic:
                    if (_subStates.values().stream().anyMatch(s -> s.is(success)))
                        _status = success;
                    else if (_subStates.values().stream().anyMatch(s -> s.is(cancelled)))
                        _status = cancelled;
                    else if (_subStates.values().stream().anyMatch(s -> s.is(failed)))
                        _subStates.values().stream().filter(s -> s.is(failed)).limit(1).findFirst().ifPresent(s -> setStatus(s.error()));
                    else _status = skipped;
                    break;
                case pessimistic:
                    if (_subStates.values().stream().anyMatch(s -> s.is(failed)))
                        _subStates.values().stream().filter(s -> s.is(failed)).limit(1).findFirst().ifPresent(s -> setStatus(s.error()));
                    else if (_subStates.values().stream().anyMatch(s -> s.is(cancelled)))
                        _status = cancelled;
                    else if (_subStates.values().stream().anyMatch(s -> s.is(success)))
                        _status = success;
                    else _status = skipped;
                    break;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StepResultState{");
        sb.append("_id='").append(_id).append('\'');
        sb.append(", _status=").append(_status);
        if (_error != null) sb.append(", _error=").append(_error);
        if (_subStates.size() > 0) sb.append(", _subStates=").append(_subStates);
        sb.append('}');
        return sb.toString();
    }
}
