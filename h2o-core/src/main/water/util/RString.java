package water.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;
import water.Key;
import water.H2O;

/**
 * A replaceable string that allows very easy and simple replacements.
 *
 * %placeholder is normally inserted
 *
 * %$placeholder is inserted in URL encoding for UTF-8 charset. This should be
 * used for all hrefs.
 *
 */
public class RString {
  // A placeholder information with replcement group and start and end labels.

  private static class Placeholder {

    LabelledStringList.Label start;
    LabelledStringList.Label end;
    RString group;

    // Creates new placeholder
    private Placeholder(LabelledStringList.Label start, LabelledStringList.Label end) {
      this.start = start;
      this.end = end;
      this.group = null;
    }

    // Creates new placeholder for replacement group
    private Placeholder(LabelledStringList.Label start, LabelledStringList.Label end, String from) {
      this.start = start;
      this.end = end;
      this.group = new RString(from, this);
    }
  }
  // Placeholders
  MMHashMap<String, Placeholder> _placeholders;

  // Parts of the final string (replacements and originals together).
  LabelledStringList _parts;
  // Parent placeholder if the RString is a replacement group.
  Placeholder _parent;

  // Passes only valid placeholder name characters
  static private boolean isIdentChar(char x) {
    return (x == '$') || ((x >= 'a') && (x <= 'z')) || ((x >= 'A') && (x <= 'Z')) || ((x >= '0') && (x <= '9')) || (x == '_');
  }

  // Creates a string that is itself a replacement group.
  private RString(final String from, Placeholder parent) {
    this(from);
    _parent = parent;
  }

  // Creates the RString from given normal string. The placeholders must begin
  // with % sign and if the placeholder name is followed by { }, the placeholder
  // is treated as a replacement group. Replacement groups cannot be tested.
  // Only letters, numbers and underscore can form a placeholder name. In the
  // constructor the string is parsed into parts and placeholders so that all
  // replacements in the future are very quick (hashmap lookup in fact).
  public RString(final String from) {
    _parts = new LabelledStringList();
    _placeholders = new MMHashMap<>();
    LabelledStringList.Label cur = _parts.begin();
    int start = 0;
    int end = 0;
    while( true ) {
      start = from.indexOf("%", end);
      if( start == -1 ) {
        cur.insertAndAdvance(from.substring(end, from.length()));
        break;
      }
      ++start;
      if( start == from.length() ) {
        throw new ArrayIndexOutOfBoundsException();
      }
      if( from.charAt(start) == '%' ) {
        cur.insertAndAdvance(from.substring(end, start));
        end = start + 1;
      } else {
        cur.insertAndAdvance(from.substring(end, start - 1));
        end = start;
        while( (end < from.length()) && (isIdentChar(from.charAt(end))) ) {
          ++end;
        }
        String pname = from.substring(start, end);
        if( (end == from.length()) || (from.charAt(end) != '{') ) {
          // it is a normal placeholder
          _placeholders.put2(pname, new Placeholder(cur.clone(), cur.clone()));
        } else {
          // it is another RString
          start = end + 1;
          end = from.indexOf("}", end);
          if( end == -1 ) {
            throw new ArrayIndexOutOfBoundsException("Missing } after replacement group");
          }
          _placeholders.put2(pname, new Placeholder(cur.clone(), cur.clone(), from.substring(start, end)));
          ++end;
        }
      }
    }
  }

  // Returns the string with all replaced material.
  @Override public String toString() { return _parts.toString(); }

  // Removes all replacements from the string (keeps the placeholders so that
  // they can be used again.
  private void clear() {
    //for( Placeholder p : _placeholders.values() ) {
    //  p.start.removeTill(p.end);
    //}
    throw H2O.unimpl();
  }

  public void replace(String what, Key key) {
    replace(what, key.user_allowed() ? key.toString() : "<code>"+key.toString()+"</code>");
  }

  // Replaces the given placeholder with an object. On a single placeholder,
  // multiple replaces can be called in which case they are appended one after
  // another in order.
  public void replace(String what, Object with) {
    if (what.charAt(0)=='$')
      throw new RuntimeException("$ is now control char that denotes URL encoding!");
    for (Placeholder p : _placeholders.get(what))
      p.end.insertAndAdvance(with.toString());

    ArrayList<Placeholder> ar = _placeholders.get("$"+what);
    if( ar == null ) return;
    for (Placeholder p : ar)
      try {
        p.end.insertAndAdvance(URLEncoder.encode(with.toString(),"UTF-8"));
      } catch (IOException e) {
        p.end.insertAndAdvance(e.toString());
      }
  }

  // Returns a replacement group of the given name and clears it so that it
  // can be filled again.
  private RString restartGroup(String what) {
    List<Placeholder> all = _placeholders.get(what);
    assert all.size() == 1;

    Placeholder result = all.get(0);
    if( result.group == null ) {
      throw new NoSuchElementException("Element " + what + " is not a group.");
    }
    result.group.clear();
    return result.group;
  }

  // If the RString itself is a replacement group, adds its contents to the
  // placeholder.
  private void append() {
    if( _parent == null ) {
      throw new UnsupportedOperationException("Cannot append if no parent is specified.");
    }
    _parent.end.insertAndAdvance(toString());
  }


  private static class MMHashMap<K,V> extends HashMap<K,ArrayList<V>> {
    void put2( K key, V val ) {
      ArrayList<V> ar = get(key);
      if( ar==null ) put(key,ar = new ArrayList<>());
      ar.add(val);
    }
  }
}

/**
 * List that has labels to it (something like copyable iterators) and some very
 * basic functionality for it.
 *
 * Since it is not a private class only the things we require are filled in. The
 * labels do not expect or deal with improper use, so make sure you know what
 * you are doing when using directly this class.
 */
class LabelledStringList {

  // Inner item of the list, single linked
  private static class Item {

    String value;
    Item next;

    Item(String value, Item next) {
      this.value = value;
      this.next = next;
    }
  }

  // Label to the list, which acts as a restricted form of an iterator. Notably
  // a label can be used to add items in the middle of the list and also to
  // delete all items in between two labels.
  class Label {
    // element before the label

    Item _prev;

    // Creates the label from given inner list item so that the label points
    // right after it. If null, label points at the very beginnig of the list.
    Label(Item prev) {
      _prev = prev;
    }

    // Creates a new copy of the label that points to the same place
    @Override protected Label clone() {
      return new Label(_prev);
    }

    // Inserts new string after the label
    private void insert(String value) {
      if( _prev == null ) {
        _begin = new Item(value, _begin);
      } else {
        _prev.next = new Item(value, _prev.next);
      }
      ++_noOfElements;
      _length += value.length();
    }

    // Inserts new string after the label and then advances the label. Thus in
    // theory inserting before the label.
    void insertAndAdvance(String value) {
      insert(value);
      if( _prev == null ) {
        _prev = _begin;
      } else {
        _prev = _prev.next;
      }
    }

    // Removes the element after the label.
    private void remove() throws NoSuchElementException {
      if( _prev == null ) {
        if( _begin == null ) {
          throw new NoSuchElementException();
        }
        _length -= _begin.value.length();
        _begin = _begin.next;
      } else {
        if( _prev.next == null ) {
          throw new NoSuchElementException();
        }
        _length -= _prev.next.value.length();
        _prev.next = _prev.next.next;
      }
      --_noOfElements;
    }

    // Removes all elements between the label and the other label. The other
    // label must come after the first label, otherwise everything after the
    // label will be deleted.
    private void removeTill(Label other) {
      if( _prev == null ) {
        if( other._prev == null ) {
          return;
        }
        while( ((_begin != null) && (_begin.next != other._prev.next)) ) {
          _length -= _begin.value.length();
          _begin = _begin.next;
          --_noOfElements;
        }
      } else {
        if( other._prev == null ) {
          clear();
          _prev = null;
        } else {
          Item end = other._prev.next;
          while( (_prev.next != null) && (_prev.next != end) ) {
            remove();
          }
        }
      }
      other._prev = _prev;
    }
  }
  // first item
  private Item _begin;
  // length in characters of the total stored string
  private int _length;
  // number of String elemets stored
  private int _noOfElements;

  // Creates an empty string list
  LabelledStringList() {
    _length = 0;
    _noOfElements = 0;
  }

  // Returns a label to the first item
  Label begin() {
    return new Label(null);
  }

  // Returns the number of elements stored in the list
  private int length() {
    return _noOfElements;
  }

  // Clears all elements in the list (all labels should be cleared by the
  // user when calling this method).
  private void clear() {
    _begin = null;
    _length = 0;
    _noOfElements = 0;
  }

  // Concatenates all parts of the string and returns them as single string
  @Override public String toString() {
    StringBuilder s = new StringBuilder(_length);
    Item i = _begin;
    while( i != null ) {
      s.append(i.value);
      i = i.next;
    }
    return s.toString();
  }
}

