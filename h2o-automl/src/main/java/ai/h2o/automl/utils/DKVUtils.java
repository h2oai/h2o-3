package ai.h2o.automl.utils;

import water.Job;
import water.Key;
import water.Lockable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class DKVUtils {

    private DKVUtils() {}

    public static <T extends Lockable<T>> void atomicUpdate(Lockable<T> target, Runnable update, Key<Job> jobKey) {
      target.write_lock(jobKey);
      try {
        update.run();
        target.update(jobKey);
      } finally {
        target.unlock(jobKey);
      } 
    }

    public static <T extends Lockable<T>> void atomicUpdate(Lockable<T> target, Runnable update, Key<Job> jobKey, ReadWriteLock lock) {
      final Lock writeLock = lock.writeLock();
      if (lock instanceof ReentrantReadWriteLock && ((ReentrantReadWriteLock.WriteLock)writeLock).isHeldByCurrentThread()) {
        writeLock.lock();
        try {
          update.run();
        } finally {
          writeLock.unlock();
        }
      } else {
        writeLock.lock();
        try {
          atomicUpdate(target, update, jobKey);
        } finally {
          writeLock.unlock();
        }
      }
    }
}
