package water.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.AutoBuffer;
import water.Freezable;
import water.Key;
import water.util.IcedHashMapBase.ArrayType;
import water.util.IcedHashMapBase.KeyType;
import water.util.IcedHashMapBase.ValueType;

import java.util.concurrent.TimeUnit;


@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class IcedHashMapBench {

    public static class SkippedException extends RuntimeException {
        public SkippedException() {
        }

        public SkippedException(String message) {
            super(message);
        }
    }

    @State(Scope.Thread)
    public static class IcedHashMapState {
//        @Param({"10", "1000"})
        @Param({"10", "1000", "100000"})
        private int n_entries;
//        @Param({"1", "1000"})
        @Param({"1", "100", "10000"})
        private int array_values_length;
//        @Param({"String", "Freezable"})
        @Param({"String"})
        private KeyType keyType;
//        @Param({"String", "Boolean", "Integer", "Double", "Freezable"})
        @Param({"String", "Boolean", "Integer", "Double"})
        private ValueType valueType;
        @Param({"None", "Array", "PrimitiveArray"})
        private IcedHashMapBase.ArrayType arrayType;

        private IcedHashMap map;

        @Setup
        public void setup() {
            if ((arrayType == ArrayType.None && array_values_length > 1)
                    || (arrayType != ArrayType.None && array_values_length == 1)) {
                throw new SkippedException();
            }

            map = new IcedHashMap();
            for (int i = 0; i < n_entries; i++) {
                map.put(makeKey(i), makeValue(i));
            }
        }

        private Object makeKey(int idx) {
            switch (keyType) {
                case String: return "key_"+idx;
                case Freezable: return Key.make("key_"+idx);
            }
            throw new SkippedException("wrong key type");
        }

        private Object makeValue(int idx) {
            switch (arrayType) {
                case None:
                    return makeSingleValue(idx);
                case Array:
                    return makeArrayValue(idx);
                case PrimitiveArray:
                    return makePrimitiveArrayValue(idx);
                default:
                    throw new SkippedException("wrong value/array type combination");
            }
        }

        private Object makeSingleValue(int idx) {
            switch (valueType) {
                case String: return "value_"+idx;
                case Boolean: return idx % 2 == 0;
                case Integer: return idx;
                case Double: return 17./(idx+1);
                case Freezable: return Key.make("value_"+idx);
                default:
                    throw new SkippedException("wrong value type");
            }
        }

        private Object[] makeArrayValue(int idx) {
            Object[] arr;
            switch (valueType) {
                case String: arr = new String[array_values_length]; break;
                case Boolean: arr = new Boolean[array_values_length]; break;
                case Integer: arr = new Integer[array_values_length]; break;
                case Double: arr = new Double[array_values_length]; break;
                case Freezable: arr = new Freezable[array_values_length]; break;
                default:
                    throw new SkippedException("wrong value/array type combination");
            }
            for (int i=0; i < array_values_length; i++) {
                arr[i] = makeSingleValue(i);
            }
            return arr;
        }

        private Object makePrimitiveArrayValue(int idx) {
            switch (valueType) {
                case Boolean:
                    boolean[] barr = new boolean[array_values_length];
                    for (int i=0; i < array_values_length; i++) { barr[i] = i%2 == 0; }
                    return barr;
                case Integer:
                    int[] iarr = new int[array_values_length];
                    for (int i=0; i < array_values_length; i++) { iarr[i] = i; }
                    return iarr;
                case Double:
                    double[] darr = new double[array_values_length];
                    for (int i=0; i < array_values_length; i++) { darr[i] = 17./(i+1); }
                    return darr;
                default:
                    throw new SkippedException("wrong value/array type combination");
            }
        }

    }


    @Benchmark
    public void writeMap(IcedHashMapState state, Blackhole bh) {
        if (state.map == null) return;

        AutoBuffer buffer = new AutoBuffer();
        buffer.put(state.map);
        bh.consume(buffer);
    }

    @Benchmark
    public void writeReadMap(IcedHashMapState state, Blackhole bh) {
        if (state.map == null) return;

        IcedHashMap read = new AutoBuffer().put(state.map).flipForReading().get();
        bh.consume(read);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(IcedHashMapBench.class.getSimpleName())
                .addProfiler(StackProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
