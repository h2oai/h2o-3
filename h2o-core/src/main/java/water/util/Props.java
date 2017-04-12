package water.util;

import water.util.fp.Function;
import water.util.fp.Functions;
import water.util.fp.PartialFunction;
import water.util.fp.Predicate;

import java.util.*;
import static water.util.fp.FP.*;
import static water.util.ArrayUtils.*;

/**
 * LDAP-like storage of properties with multilevel (dot-separated) keys
 * 
 * Backported from scalakittens.Props
 * 
 * Created by vpatryshev on 4/10/17.
 */
public class Props extends PartialFunction<String, String> {
  private Map<String, String> innerMap;
  
  private Props(Map<String, String> innerMap) {
    this.innerMap = innerMap;
  }
  
  static Props props(Map<String, String> innerMap) {
    return new Props(innerMap);
  }
  
  public Props filterValues(Predicate<String> p) {
    Map<String, String> out = new HashMap<>();
    for (Map.Entry<String, String> e : innerMap.entrySet()) {
      if (p.apply(e.getValue())) out.put(e.getKey(), e.getValue());
    }
    return props(out);
  }

  public Props filterKeys(Predicate<String> p) {
    Map<String, String> out = new HashMap<>();
    for (Map.Entry<String, String> e : innerMap.entrySet()) {
      if (p.apply(e.getKey())) out.put(e.getKey(), e.getValue());
    }
    return props(out);
  }
  
  private static String[] keyAsArray(String key) {
    return key.split("\\.");
  }

  private static List<String> keyAsList(String key) {
    return new ArrayList<>(Arrays.asList(key.split("\\.")));
  }
  
  private static Set<String> keyAsSet(String key) {
    return toSet(keyAsArray(key));
  }

  private static String mkKey(Iterable<String> ks) {
    return StringUtils.join(".", ks);
  }
  
  public boolean keyMatches(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    return plk.equals(slk) || looseMatch(slk, plk);
  }

  private boolean looseMatch(String suggested, String present) {
    String[] ourKeys = keyAsArray(present);
    String[] suggestedKeys = keyAsArray(suggested);
    int ourPtr = 0;
    for (String element : suggestedKeys) {
      while (ourPtr++ < ourKeys.length) {
        if (ourKeys[ourPtr].matches(element)) break;
      }
      if (ourPtr > ourKeys.length || !ourKeys[ourPtr-1].matches(element)) return false;
    }
    return true;
  }
  
  static boolean keyContains(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    if (slk.equals(plk)) return true;
    
    String[] sks = keyAsArray(slk);
    String[] pks = keyAsArray(slk);
    if (sks.length != pks.length) return false;
    
    for (int i = 0; i < sks.length; i++) {
      if (!pks[i].contains(sks[i])) return false;
    }
    
    return true;
  }
  
  private Predicate<String> containsSubkey(final String subkey) {
    return new Predicate<String>() {
      @Override public Boolean apply(String key) {
        return keyAsSet(key).contains(subkey);
      }
    };
  }

  Set<String> matchingKeys(String key) {
    Set<String> s = new HashSet<>();
    for (String k : innerMap.keySet()) if (keyMatches(key, k)) s.add(k);
    return s;
  }

  @SuppressWarnings("incomplete-switch")
  public Option<String> findKey(String key) {
    if (innerMap.keySet().contains(key))                        return Some(key);
    for (String k : innerMap.keySet()) if (keyMatches(key, k))  return Some(k);
    for (String k : innerMap.keySet()) if (keyContains(key, k)) return Some(k);
    return none();    
  }

  public Option<String> findHaving(String key) {
    if (innerMap.keySet().contains(key))                        return get(key);
    for (String k : innerMap.keySet()) if (keyMatches(key, k))  return get(k);
    for (String k : innerMap.keySet()) if (keyContains(key, k)) return get(k);
    return none();
  }
  
  public Props findAllHaving(String key) {
    return filterKeys(containsSubkey(key));
  }
  
  public Props findAndReplace(String key, String value) {
    Map<String, String> newOne = new HashMap<>();
    newOne.putAll(innerMap);
    
    Props found = findAllHaving(key);
    for (String k : found.innerMap.keySet()) { newOne.put(k, value); }
    
    return props(newOne);
  }

  private static boolean containsKeyBundle(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    if (slk.equals(plk)) return true;
    Set<String> pks = keyAsSet(plk);

    for (String sk : keyAsArray(slk)) {
      if (!pks.contains(sk.replaceAll("\\?", "."))) return false;
    }
    return true;
  }
  
  private PartialFunction<String, String> ForKey = new PartialFunction<String, String>() {
    @Override public Option<String> apply(String s) {
      return Option(innerMap.get(s));
    }
  };
  
  @Override
  public Option<String> apply(String s) {
    return findKey(s).flatMap(ForKey);
  }
  
  public Option<String> forSomeKey(String... collectionOfKeys) {
    for (String key : collectionOfKeys) 
      if (innerMap.containsKey(key)) 
        return Option(innerMap.get(key));
        
    return none();    
  }

  @SuppressWarnings("incomplete-switch")
  public Option<String> findIgnoringKeyOrder(String key) {
    for (String k : innerMap.keySet()) 
      if (containsKeyBundle(key, k)) 
        return apply(k);
    
    return none();
  }
  
  public boolean isDefinedAt(String key) {
    return innerMap.containsKey(key);
  }
  
  public Option<String> get(Object key) { return apply(""+key); }
  public Option<String> get(Object k1, Object k2) { 
    return get(""+k1+"."+k2); 
  }
  public Option<String> get(Object k1, Object k2, Object k3) { 
    return get(""+k1+"."+k2+"."+k3); 
  }
  public Option<String> get(Object k1, Object k2, Object k3, Object k4) { 
    return get(""+k1+"."+k2+"."+k3+"."+k4); 
  }

  public Option<Integer> intAt(Object key) {
    return get(key).flatMap(Functions.parseInt);
  }

  public Option<Double> doubleAt(Object key) {
    return get(key).flatMap(Functions.parseDouble);
  }
  
  public Option<String> oneOf(String... keys) {
    for (String k : keys) if (isDefinedAt(k)) return apply(k);
    
    return none();
  }
  
  public String applyOrElse(String key, Function<String, String> otherwise) {
    String fromMap = innerMap.get(key);
    return fromMap != null ? fromMap : otherwise.apply(key);
  }

  public String getOrElse(String key, String otherwise) {
    String fromMap = innerMap.get(key);
    return fromMap != null ? fromMap : otherwise;
  }
  
  public boolean  isEmpty() { return  innerMap.isEmpty(); }
  public boolean nonEmpty() { return !innerMap.isEmpty(); }

  private static List<String> dropIndexKeysInSequence(String[] keys) {
    List<String> out = new LinkedList<>();
    for (String k : keys) if (!ArrayUtils.isInt(k) && !k.isEmpty()) out.add(k);
    return out;
  }
  
  private static Map<String, String> dropIndexKeys(Map<String, String> m) { 
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> kv : m.entrySet()) {
      String[] ks = keyAsArray(kv.getKey());
      List<String> cleanedUp = dropIndexKeysInSequence(ks);
      if (!cleanedUp.isEmpty()) {
        String newKey = mkKey(cleanedUp);
        result.put(newKey, kv.getValue());
      }
    }
    return result;
  }
  
  public Props dropIndexes() {
    return props(dropIndexKeys(innerMap));
  }
  
  public String toFormattedString() { return formatted(0); }

  protected String formatted(int pad) {
    return spaces(pad) + (
    isEmpty()? "Empty()" : "fp(\n" + formatPf(pad + 2) + "\n" + spaces(pad)
    )+")";
  }

  private static String spaces(int n) { return StringUtils.repeat(" ", n); }
  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      sb.append(c < 128 ? (""+c) : String.format("\\u%04x", (int)c));
    }
    return sb.toString();
  }

  private static String quote(String s) { return "\"" + escape(s) + "\""; }

  private String mkString(String separator) {
    String[] keys = innerMap.keySet().toArray(new String[innerMap.size()]);
    Arrays.sort(keys);
    StringBuilder sb = new StringBuilder();
    for (String k : keys) {
      if (sb.length() > 0) sb.append(separator);
      sb.append(quote(k)).append(" → ").append(quote(innerMap.get(k)));
    }
    
    return sb.toString();
  }

  private String formatPf(int pad) {
    return "Map(" + mkString("\n" + spaces(pad)) + ")";
  }

  @Override public String toString() {
    return isEmpty() ? "Empty()": "fp(Map(" + mkString(", ") + "))";
  }
  
  @Override public boolean equals(Object other) {
    return other instanceof Props && innerMap.equals(((Props) other).innerMap);
  }
  
  @Override public int hashCode() {
    return 2017*innerMap.hashCode();
  }
  
  public boolean hasKey(String key) { return findKey(key).nonEmpty(); }
  
  public Props mapKeys(Function<String, String> mapper) {
    if (isEmpty()) return this;
    
    Map<String, String> newMap = new HashMap<>();
    for (String k : innerMap.keySet()) {
      String newKey = mkKey(Functions.map(keyAsList(k), mapper));
      newMap.put(newKey, innerMap.get(k));
    }
    return props(newMap);
  }

  public Props translateBack(Map<String, String> dictionary) {
    Function<String, String> mapper = Functions.forMap(dictionary, Functions.<String, String>identity());
    return mapKeys(mapper);
  }
  
  public Props translate(Map<String, String> dictionary) {
    Map<String, String> inverted = new HashMap<>(dictionary.size());
    for (Map.Entry<String, String> p : dictionary.entrySet()) {
      inverted.put(p.getValue(), p.getKey());
    }

    return translateBack(inverted);
  }
  
  public Props translate(Props dictionary) {
    return isEmpty() ? this : translate(dictionary.innerMap);
  }

  private static Map<String, String> dropPrefixInMap(Map<String, String> map) {
    Map<String, String> out = new HashMap<>();
    for (String k : map.keySet()) {
      String[] split = keyAsArray(k);
      if (split.length > 1) {
        String newKey = StringUtils.join(".", split, 1, split.length);
        out.put(newKey, map.get(k));
      }
    }
    return out;
  }

  public Props dropPrefix() {
    return props(dropPrefixInMap(innerMap));
  }
  
  public static Map<String, String> subtreeOfMap(Map<String, String> map, String key) {
    Map<String, String> out1 = new HashMap<>();
    for (String k : map.keySet()) {
      if (k.startsWith(key+".")) out1.put(k.substring(key.length() + 1), map.get(k));
    }
    Map<String, String> downOneLevel = dropPrefixInMap(map);
    if (!downOneLevel.isEmpty()) out1.putAll(subtreeOfMap(downOneLevel, key));
    
    return out1;
  }
  
  public Props subtree(String key) {
    return key.isEmpty() ? this : props(subtreeOfMap(innerMap, key));
  }
  
  public Props dropAllPrefixes() {
    Map<String, String> out = new HashMap<>();
    for (String k : innerMap.keySet()) {
      String[] split = keyAsArray(k);
      if (split.length > 1) {
        String newKey = split[split.length - 1];
        out.put(newKey, innerMap.get(k));
      }
    }
    return props(out);
  }
  
  public Set<String> fullKeys() { return innerMap.keySet(); }

  public Set<String> keySet() {
    Set<String> out = new HashSet<>();
    for (String k : fullKeys()) out.add(keyAsArray(k)[0]);
    return out;
  }
  
  public Set<String> commonKeys(Props other) {
    Set<String> out = new HashSet<>(fullKeys());
    out.retainAll(other.fullKeys());
    return out;
  }
  
  public Props trimPrefixes() {
    final boolean singleRoot = keySet().size() == 1;
    final boolean compoundKeys = innerMap.keySet().iterator().next().contains(".");
    return singleRoot && compoundKeys ? dropPrefix().trimPrefixes() : this;
  }

  public static String numberKey(int i) {
    return "[[" + i + "]]";
  }

  private final static Predicate<String> NUMBER_PATTERN = Predicate.matching("\\[\\[(\\d+)\\]\\]\\..*");
  
  public Props allIndexed() { return filterKeys(NUMBER_PATTERN); }

  public boolean isAnArray() {
    return fullKeys().equals(NUMBER_PATTERN.filter(fullKeys()));
  }
  
  public Props findAllHavingNumber(int i) {
    return findAllHaving(numberKey(i));
  }
  
  private Props addPrefix(String prefix) {
    if (prefix.trim().isEmpty()) return this;
    Map<String, String> newMap = new HashMap<>();
    for (String k : innerMap.keySet()) newMap.put(prefix+"."+k, innerMap.get(k));
    return props(newMap);
  }  
  
  private Props addNumber(int i) {
    return addPrefix(numberKey(i));
  }
  
  public Props addAll(Map<String, String> other) {
    if (other.isEmpty()) return this;
    Map<String, String> newMap = new HashMap<>(innerMap);
    newMap.putAll(other);
    return props(newMap);
  }

  public Props addAll(Props other) {
    return isEmpty() ? other : addAll(other.innerMap);
  }
  
  public Props startingWith(String prefix) {
    Map<String, String> out = new HashMap<>();
    for (String k : innerMap.keySet()) 
      if (k.startsWith(prefix + ".")) 
        out.put(k.substring(prefix.length() + 1), innerMap.get(k));
  
    return props(out);
  }

  public Props endingWith(String prefix) {
    Map<String, String> out = new HashMap<>();
    for (String k : innerMap.keySet())
      if (k.endsWith(prefix + "."))
        out.put(k.substring(0, k.length() - prefix.length() - 1), innerMap.get(k));

    return props(out);
  } 

  private String stringifyAt(String key) {
    Props sub = subtree(key);
    if (sub.nonEmpty()) return sub.toJsonString();
    Option<String> vOpt = get(key);
    return vOpt.isEmpty() ? "" : quote(vOpt.getOrNull());
  }

  int maxIndex() {
    int max = 0;
    for (String k : keySet()) if (NUMBER_PATTERN.apply(k)) try {
      max = Math.max(max, Integer.parseInt(k.substring(2, k.length() - 2)));
    } catch (Exception ignore) {}
    
    return max;
  }  
  
  public String toJsonString() {
    StringBuilder sb = new StringBuilder();

    if (isAnArray()) {
      for (int i = 1; i < maxIndex(); i++) {
        if (sb.length() == 0) sb.append(", ");
        sb.append(stringifyAt(numberKey(i)));
      }
      return "[" + sb + "]";
    } else {
      for (String k : keySet()) {
        if (sb.length() == 0) sb.append(", ");
        sb.append(quote(k)).append(":").append(stringifyAt(k));
      }
      return "{" + sb + "}";
    }
  }
  
  private static void addPair(Map<String, String> map, Object x, Object y) {
    if (x != null && y != null) map.put(x.toString(), y.toString());
  }

  private static void addPair(Map<String, String> map, Map.Entry<?, ?> p) {
    addPair(map, p.getKey(), p.getValue());
  }
  
  public static Props props(Map.Entry<?,?>... pairs) {
    Map<String, String> out = new HashMap<>();
    for (Map.Entry<?,?>p : pairs) addPair(out, p);
    
    return props(out);
  }
  
  public static Props fromTable(Iterable<?> table) {
    Map<String, String> out = new HashMap<>();
    
    for (Object o : table) {
      if (o instanceof Iterable<?>) {
        Iterator<?> it = ((Iterable<?>)o).iterator();
        if (it.hasNext()) {
          Object x = it.next();
          if (it.hasNext()) addPair(out, x, it.next());
        }
      } else if (o.getClass().isArray()) {
        Object[] xs = (Object[]) o;
        if (xs.length == 2) {
          addPair(out, xs[0], xs[1]);
        }
      } else if (o instanceof Map.Entry<?,?>) {
        addPair(out, (Map.Entry<?, ?>)o);
      }
    }
    return props(out);
  }
  
  public static Props fromString(Iterable<String> source, String separator) {
    Map<String, String> out = new HashMap<>();
    for (String s : source) {
      String[] split = s.split(separator);
      if (split.length == 2) addPair(out, split[0].trim(), split[1].trim());
    }
    return props(out);
  }
    /*
   */

}
/*
import scala.io.Source
import scala.util.parsing.combinator.RegexParsers

    def reorder(paramsOrder:Int*):Props = if (isEmpty) this else {
    val keyMap = paramsOrder.zipWithIndex.toMap
    val newOrder = 1 to paramsOrder.size map keyMap

    def remap(key: String) = {
    val keys = key split "\\."
    if (keys.length < paramsOrder.length) key
    else newOrder map keys mkString "."
    }

    Props(innerMap map { case(k,v) ⇒ remap(k) → v })
    }

    trait PropsOps {
    val Id:String⇒String = identity

private lazy val PropertyFormat = "([\\w\\.]+) *= *(.*)".r

    def fromSource(source: ⇒Source): Props = {
    var lines = Result.forValue(source.getLines().toList).getOrElse(List(""))

    fromPropLines(lines)
    }

    def fromPropLines(lines: Seq[String]): Props = {
    val QQ = "\\\"(.*)\\\"$".r
    def unquote(s: String) = s match {
    case QQ(s1) ⇒ s1
    case s2     ⇒ s2
    }
    def trim(s: String) = unquote(s.trim)
    val map = lines.
    filter(line ⇒ !line.startsWith("#") && !line.isEmpty).
    collect { case PropertyFormat(key, value) ⇒ key → trim(value) }.
    toMap

    props(map)
    }

    // opportunistically extract props from sequence of strings, some of them being empty etc
    def fromParallelLists(names: List[String], values: List[String], expected: List[String]): Result[Props] = {
    val missing = expected.map(_.toLowerCase).toSet diff names.map(_.toLowerCase).toSet
    val result: Result[Props] = Good(props(names zip values toMap))
    result.filter((p:Props) ⇒ missing.isEmpty, s"Missing column(s) (${missing mkString ","}) in ${names mkString ","}")
    }

    def fromList(source:List[String]) = propsFromTable(source.zipWithIndex map { case (a, i) ⇒ numberKey(i+1) → a})

    def isPrimitive(x: Any) = x match {
    case u: Unit    ⇒ true
    case z: Boolean ⇒ true
    case b: Byte    ⇒ true
    case c: Char    ⇒ true
    case s: Short   ⇒ true
    case i: Int     ⇒ true
    case j: Long    ⇒ true
    case f: Float   ⇒ true
    case d: Double  ⇒ true
    case _          ⇒ false
    }

private[scalakittens] def parsePair(k:Any, v:Any):Result[Props] = v match {
    case s:String            ⇒ Good(props(k.toString → s))
    case x if isPrimitive(x) ⇒ Good(props(k.toString → v.toString))
    case other               ⇒ fromTree(other) map(_ addPrefix k.toString)
    }

    def fromTree(source:Any):Result[Props] = source match {
    case l: List[_]  ⇒ Good(fromList(l map (_.toString)))
    case m: Map[_,_] ⇒
    val pairs: TraversableOnce[Result[Props]] = m map (parsePair _).tupled
    val result: Result[Traversable[Props]] = Result traverse pairs
    result map Props.accumulate
    case bs          ⇒ Result.error(s"Cannot extract properties from $bs")
    }

    }

    object Props extends PropsOps {

    var DEPTH_COUNTER = 0

    val sNumberKey = "\\[\\[(\\d+)\\]\\]"
    val NumberKeyPattern = sNumberKey.r
    lazy val empty = Props(Map.empty)
    val isNumberKey = (s:String) ⇒ s matches sNumberKey

//  def unapply(fp: Props): Option[PropMap] = Some(fp.innerMap)

private val prefixSizeLimits = (1, 70)

private def goodSize(s: String): Boolean = s.length >= prefixSizeLimits._1 && s.length <= prefixSizeLimits._2

private def goodCharacter(s: String): Boolean = !("$0" contains s(0))


    def accumulate(pp: TraversableOnce[Props]): Props = (Props.empty /: pp)(_++_)
    def fold(collection: TraversableOnce[Props]) = (empty /: collection)(_++_)

    def foldWithIndex(pss: TraversableOnce[Props]): Props = accumulate(pss.toList.zipWithIndex.map { case (ps, i) ⇒ ps.addNumber(i)})

private def isaLols(x: Any) = x match {
    case l: List[_] ⇒ l.forall({
    case ll: List[_] ⇒ ll.forall(_.isInstanceOf[String])
    case _ ⇒ false
    })
    case _ ⇒ false
    }

    // todo(vlad): civilize it
    def collectProps(source: Any): Result[Seq[Props]] = source match {
    case lols: List[_] if lols.forall(isaLols) ⇒
    val goodLols = lols.asInstanceOf[List[List[List[String]]]]
    val props = goodLols map propsFromTable
    Good(props)

    case x ⇒ Result.error(s"Failed to retrieve props from table: $x in $source")
    }

private def sameKeyBundle(theKeyWeHave: String, suggestedAnyCase: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keys = ourKey.split("\\.").toSet
    val suggestedKeys = suggested.split("\\.") .map (_.replaceAll("\\?", ".")) .toSet

    keys == suggestedKeys
    }
    }

private def containsKeyBundle(theKeyWeHave: String, suggestedAnyCase: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keys = ourKey.split("\\.").toSet
    val suggestedKeys = suggested.split("\\.") .map (_.replaceAll("\\?", ".")) .toSet

    suggestedKeys subsetOf keys
    }
    }

private def dropIndexKeysInSequence(keys: Seq[String]) = keys map {
    case NumberKeyPattern(i) ⇒ ""
    case x ⇒ x
    } filter (!_.isEmpty)

private def dropIndexKeys(map: PropMap) = {
    val withSplitKeys = map.map(kv ⇒ (kv._1.split('.'), kv._2))
    val cleanedUpKeys = withSplitKeys.map(kv ⇒ (dropIndexKeysInSequence(kv._1), kv._2))
    val withGoodKeys = cleanedUpKeys.filter(kv ⇒ kv._1.nonEmpty)
    val withNewKeys = withGoodKeys.map(kv ⇒ (kv._1 mkString ".", kv._2))
    withNewKeys
    }

private def mapify(seq: Seq[Any]) = seq.zipWithIndex map {
    case (x, i) ⇒ numberKey(i+1) → x
    } toMap

    def fromMap(source: Map[_, _]): Props = {
    val collection: Iterable[Props] = source map {
    case (k0, v) ⇒
    val k = k0.toString
    v match {
    case null        ⇒ Props.empty
    case m:Map[_, _] ⇒ fromMap(m).addPrefix(k)
    case s:Seq[_]    ⇒ fromMap(mapify(s)).addPrefix(k)
    case x: Any      ⇒ props(k → (""+x))
    }
    }
    accumulate(collection)
    }

    val parse = new RegexParsers {
    override def skipWhitespace = false
    def number = "\\d+".r
    def numbers = "(" ~> repsep(number, ",") <~ ")"
    def text = "\"" ~> "[^\"]+".r <~ "\""
    def mapPair:Parser[(String, String)] = text ~ " → " ~ text ^^ {
    case k ~ _ ~ v ⇒ k → v
    }

    def mapContents:Parser[List[(String, String)]] = (mapPair ~ rep(",\\s*".r ~> mapPair)) ^^ {
    case h ~ t ⇒ h::t
    }

    // TODO: have tests pass with toFormattedString

    def eol: Parser[Any] = """(\r?\n)+""".r

    def mapExp: Parser[PropMap] = "Map\\s*\\(\\s*".r ~> mapContents <~ "\\s*".r <~ rep(eol) <~")" ^^ (_.toMap)

    def propMapExp: Parser[Props] = "fp\\s*\\(\\s*".r ~> mapExp <~ "\\s*".r <~ rep(eol) <~")" ^^ props

    def withDictionary = " with dictionary " ~> mapExp

    def withReorder = " with reordering " ~> numbers ^^ {_ map (_.toInt)}

    def optionally[X, Y](opt: Option[Y], f: Y ⇒ X ⇒ X): (X ⇒ X) = opt map f getOrElse identity[X]

    def propExp = propMapExp ~ (withDictionary ?) ~ (withReorder ?) ^^ {
    case props ~ dictionaryOpt ~ reorderOpt ⇒
    val withDictionary = (dictionaryOpt fold props) (props translate)
    (reorderOpt fold withDictionary) (withDictionary reorder)
    }

    def apply(s0: String) = {
    val noNL = s0.replaceAll("\\n", " ").trim
    parseAll(propExp, noNL) match {
    case Success(result, _) ⇒ Good(result)
    case NoSuccess(x, y) ⇒ Result.error(s"Failed to parse: $x, $y")
    }
    }
    }

private[scalakittens] def looseMatch(suggested: String, existing: String) = {
    val keySequence = existing.split("\\.").toList

    val suggestedSequence = suggested.split("\\.").toList map (_.replaceAll("\\?", "."))

    def match1(sample: String, segment:String): Outcome = Result.forValue(segment matches sample) filter identity

    def findNext(seq: List[String], sample: String) = seq dropWhile (!match1(sample, _))

    val found = (keySequence /: suggestedSequence)((ks, sample) ⇒ findNext(ks, sample))

    found.nonEmpty
    }


    def keyContains(suggestedAnyCase: String)(theKeyWeHave: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keySequence = ourKey.split("\\.").toList
    val suggestedSequence = suggested.split("\\.").toList map (_.replaceAll("\\?", "."))
    keySequence.length == suggestedSequence.length &&
    (
    try {
    keySequence zip suggestedSequence forall {
    case (key, s) ⇒ key contains s
    }
    } catch {
    case soe:Throwable ⇒
    false
    })
    }
    }
    }



    def trimPrefixesWhile(p: String⇒Boolean, collectedPrefixes:List[String] = Nil):(Props, String) = {
    if (keySet.size == 1 && p(keySet.head)) {
    val prefix = keySet.head
    val t = dropPrefix
    t.trimPrefixesWhile(p, prefix::collectedPrefixes)
    } else (self, collectedPrefixes.reverse mkString ".")
    }

private def groupForIndexRange(indexRange: Range.Inclusive): Seq[Props] = {
    val result: Seq[Props] = indexRange map (i ⇒ {
    val key = numberKey(i)
    val havingThisNumber = findAllHavingNumber(i)
    val droppedUpToNumber = havingThisNumber.trimPrefixesWhile(key !=)._1
    droppedUpToNumber.dropPrefix
    })
    result
    }

private[scalakittens] lazy val indexRange = {
    val indexes = keySet collect { case NumberKeyPattern(n) ⇒ n.toInt }
    1 to (if (indexes.isEmpty) 0 else indexes.max)
    }

    def extractIndexed: Seq[Props] = {
    if (indexRange.toSet subsetOf indexRange.toSet) {
    groupForIndexRange(indexRange)
    } else this::Nil
    }

    def groupByIndex: Seq[Props] = {
    if (keySet forall isNumberKey) extractIndexed
    else this::Nil
    }

    def extractAllNumberedHaving(keys: String): Seq[Props] = {
    val containingKeys = findAllHaving(keys)
    val found = containingKeys.trimPrefixesWhile(key ⇒ !isNumberKey(key))
    val allIndexed = subtree(found._2)
    allIndexed groupByIndex
    }

private def trimKey(key: String) = {
    val found = key split "\n" find (!_.isEmpty) getOrElse ""
    val trimmed = found split "\\." map (_.trim) filter goodSize filter goodCharacter mkString "."
    trimmed
    }

*/