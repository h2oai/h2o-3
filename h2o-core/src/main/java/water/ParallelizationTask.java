package water;

import water.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

public class ParallelizationTask<T extends H2O.H2OCountedCompleter<T>> extends H2O.H2OCountedCompleter {
    private final transient AtomicInteger _ctr; // Concurrency control
    private final T[] _tasks; // Task holder
    private final Job<?> _j; //Keep track of job progress
    private final int _maxParallelTasks;

    public ParallelizationTask(T[] tasks, int maxParallelTasks, Job<?> j) {
        if (maxParallelTasks <= 0) {
            throw new IllegalArgumentException("Argument maxParallelTasks should be a positive integer, got: " + maxParallelTasks);
        }
        _maxParallelTasks = maxParallelTasks;
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
        _tasks[task].setCompleter(new Callback(task));
        _tasks[task].fork();
    }

    private class Callback extends H2O.H2OCallback {
        private final int _taskId;
        
        Callback(int taskId) {
            super(ParallelizationTask.this);
            _taskId = taskId;
        }

        @Override 
        public void callback(H2O.H2OCountedCompleter cc) {
            _tasks[_taskId] = null; // mark completed
            if (_j != null) {
                if (_j.stop_requested()) {
                    final int current = _ctr.get();
                    Log.info("Skipping execution of last " + (_tasks.length - current) + " out of " + _tasks.length + " tasks.");
                    stopAll();
                    throw new Job.JobCancelledException();
                }
            }
            int i = _ctr.incrementAndGet();
            if (i < _tasks.length)
                asyncVecTask(i);
        }
    }

    private void stopAll() {
        for (final T task : _tasks) {
            if (task != null) {
                task.cancel(true);
            }
        }
    }

}
