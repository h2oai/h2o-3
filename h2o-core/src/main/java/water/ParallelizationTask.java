package water;

import java.util.concurrent.atomic.AtomicInteger;

public class ParallelizationTask<T extends H2O.H2OCountedCompleter<T>> extends H2O.H2OCountedCompleter {
    private final AtomicInteger _ctr; // Concurrency control
    private static int DEFAULT_MAX_PARALLEL_TASKS = -1;
    private final T[] _tasks; // Task holder
    private final Job _j; //Keep track of job progress
    transient private int _maxParallelTasks;

    public ParallelizationTask(T[] tasks, Job j) {
        this(tasks, DEFAULT_MAX_PARALLEL_TASKS, j);
    }

    public ParallelizationTask(T[] tasks, int maxParallelTasks, Job j) {
        _maxParallelTasks = maxParallelTasks > 0 ? maxParallelTasks : H2O.SELF._heartbeat._num_cpus;
        _ctr = new AtomicInteger(_maxParallelTasks - 1);
        _tasks = tasks;
        _j = j;
    }

    @Override public void compute2() {
        final int nTasks = _tasks.length;
        addToPendingCount(nTasks-1);
        for (int i=0; i < Math.min(_maxParallelTasks, nTasks); ++i) asyncVecTask(i);
    }

    private void asyncVecTask(final int task) {
        _tasks[task].setCompleter(new Callback());
        _tasks[task].fork();
    }

    private class Callback extends H2O.H2OCallback{
        public Callback(){super(ParallelizationTask.this);}
        @Override public void callback(H2O.H2OCountedCompleter cc) {
            if(_j != null) {
                _j.update(1);
            }
            int i = _ctr.incrementAndGet();
            if (i < _tasks.length)
                asyncVecTask(i);
        }
    }
}
