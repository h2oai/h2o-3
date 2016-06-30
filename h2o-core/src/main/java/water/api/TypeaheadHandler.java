package water.api;

import water.H2O;
import water.api.schemas3.TypeaheadV3;

import java.util.ArrayList;
import java.util.List;

class TypeaheadHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TypeaheadV3 files(int version, TypeaheadV3 t) {
    List<String> matches = H2O.getPM().calcTypeaheadMatches(t.src, t.limit);
    t.matches = matches.toArray(new String[matches.size()]);
    return t;
  }
}
