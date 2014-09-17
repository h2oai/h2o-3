package water.init;

import water.util.ArrayUtils;
import water.util.Log;
import water.util.Timer;

public class MemoryBandwidth {
  public static void main(String[] args) {
    int num_threads = Runtime.getRuntime().availableProcessors();
    double membw = run(num_threads);
    Log.info("Memory bandwidth (" + num_threads + " cores) : " + membw + " GB/s.");
  }

  /**
   * Compute memory bandwidth in bytes / second
   */
  public static double run(int num_threads) {

    final double membw[] = new double[num_threads];
    Thread[] threads = new Thread[num_threads];
    for (int t=0;t<num_threads;++t) {
      final int thread_num = t;
      threads[t] = new Thread() {
        public void run() {
          MemoryBandwidth l = new MemoryBandwidth();
          membw[thread_num] = l.run_benchmark();
        }
      };
    }
    for (int t=0;t<num_threads;++t) {
      threads[t].start();
    }
    for (int t=0;t<num_threads;++t) {
      try {
        threads[t].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    return ArrayUtils.sum(membw);
  }

  // memory bandwidth in bytes / second
  double run_benchmark() {
    // use the lesser of 40MB or 10% of Heap
    final long M = Math.min(10000000l, Runtime.getRuntime().maxMemory()/10);
    int[] vals = water.MemoryManager.malloc4((int)M);
    double total;
    int repeats = 20;
    Timer timer = new Timer(); //ms
    long sum = 0;
    // write repeats * M ints
    // read  repeats * M ints
    for (int l=repeats-1; l>=0; --l) {
      for (int i=0; i<M; ++i) {
        vals[i] = i + l;
      }
      sum = 0;
      for (int i=0; i<M; ++i) {
        sum += vals[i];
      }
    }
    total = (double)timer.time()/1000./repeats;
    //use the sum in a way that doesn't affect the result (don't want the compiler to optimize it away)
    double time = total + ((M*(M-1)/2) - sum); // == total
    return (double)2*M*4/time; //(read+write) * 4 bytes
  }

}
