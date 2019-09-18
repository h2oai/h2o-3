package water.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import water.*;
import water.nbhm.NonBlockingHashMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class IcedHashSetTest extends TestUtil {

  @Before
  public void setUp() throws Exception {
    stall_till_cloudsize(1);
  }

  @Mock
  private NonBlockingHashMap<IcedVal, IcedVal> mapMock;

  @Mock
  private IcedVal icedValMock;
  
  @InjectMocks
  private IcedHashSet<IcedVal> icedSet;

  @Test
  public void addIfAbsent() {
    when(mapMock.putIfAbsent(icedValMock, icedValMock)).thenReturn(icedValMock);
    IcedVal result = icedSet.addIfAbsent(icedValMock);
    assertSame(icedValMock, result);
    verify(mapMock).putIfAbsent(icedValMock, icedValMock);
  }

  @Test
  public void get() {
    when(mapMock.getk(icedValMock)).thenReturn(icedValMock);
    IcedVal result = icedSet.get(icedValMock);
    assertSame(icedValMock, result);
    verify(mapMock).getk(icedValMock);
  }

  @Test
  public void size() {
    when(mapMock.size()).thenReturn(42);
    assertEquals(42, icedSet.size());
  }

  @Test
  public void isEmpty() {
    when(mapMock.isEmpty()).thenReturn(true);
    assertTrue(icedSet.isEmpty());
    verify(mapMock).isEmpty();
  }

  @Test
  public void contains() {
    when(mapMock.containsKey(icedValMock)).thenReturn(true);
    assertTrue(icedSet.contains(icedValMock));
    verify(mapMock).containsKey(icedValMock);

  }

  @Test
  public void iterator() {
    Collection<IcedVal> values = Collections.singletonList(icedValMock);
    when(mapMock.values()).thenReturn(values);
    assertSame(icedValMock, icedSet.iterator().next());
  }

  @Test
  public void toArray() {
    Collection<IcedVal> values = Collections.singletonList(icedValMock);
    Object[] array = new Object[]{icedValMock};
    when(mapMock.values()).thenReturn(values);
    assertArrayEquals(array, icedSet.toArray());
  }

  @Test
  public void add() {
    when(mapMock.putIfAbsent(icedValMock, icedValMock)).thenReturn(icedValMock);
    assertFalse(icedSet.add(icedValMock));
    verify(mapMock).putIfAbsent(icedValMock, icedValMock);
  }

  @Test
  public void remove() {
    assertFalse(icedSet.remove(icedValMock));
    verify(mapMock).remove(icedValMock, icedValMock);
  }

  @Test
  public void containsAll() {
    Collection<IcedVal> vals = Collections.emptyList();
    Set<IcedVal> setValues = Collections.singleton(icedValMock);
    when(mapMock.keySet()).thenReturn(setValues);
    assertTrue(icedSet.containsAll(vals));
  }

  @Test
  public void addAll() {
    IcedVal v1 = mock(IcedVal.class);
    IcedVal v2 = mock(IcedVal.class);
    Collection<IcedVal> vals = Arrays.asList(v1, v2);
    icedSet.addAll(vals);
    verify(mapMock).putIfAbsent(v1, v1);
    verify(mapMock).putIfAbsent(v2, v2);
  }

  @Test
  public void clear() {
    icedSet.clear();
    verify(mapMock).clear();
  }

  @Test
  public void testReadWrite() {
    IcedHashSet<IcedVal> set = new IcedHashSet<>();
    set.add(new IcedVal(42));
    set.add(new IcedVal(7));

    Key k = Key.make();
    DKV.put(k, set);
    Value val = DKV.get(k);
    DKV.remove(k);
    val.freePOJO();
    IcedHashSet<IcedVal> fromDKV = val.get();
    assertNotSame(set, fromDKV);
    assertEquals(set, fromDKV);
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new AutoBuffer(out, true)
            .put(set)
            .close();
    byte[] bytes = out.toByteArray();
    assertNotNull(bytes);

    ByteArrayInputStream in = new ByteArrayInputStream(bytes);
    IcedHashSet<IcedVal> deserialized = new AutoBuffer(in).get();
    assertEquals(set, deserialized);
  }

  @Test
  public void writeJSON_impl() {
    IcedHashSet<IcedVal> set = new IcedHashSet<>();
    set.add(new IcedVal(42));
    set.add(new IcedVal(7));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new AutoBuffer(out, false)
            .putJSON(set)
            .close();

    assertEquals((char) 0 + "{{\"_val\":7}, {\"_val\":42}}", out.toString());
  }

  private static class IcedVal extends Iced<IcedVal> {
    final int _val;

    public IcedVal() {
      _val = -1;
    }

    IcedVal(int val) {
      _val = val;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IcedVal icedVal = (IcedVal) o;

      return _val == icedVal._val;
    }

    @Override
    public int hashCode() {
      return _val;
    }
  }

}
