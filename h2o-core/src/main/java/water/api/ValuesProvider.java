package water.api;

/**
 * A marker type to provide values for type which behaves like Enum in REST API.
 *
 * For example String type, which has known set of possible values.
 *
 * Note: we need this provider since we cannot reference non-constant expression inside
 * @API annotation. For example, @API(values = ParserService.INSTANCE.NAMES) is denied!
 * So ValuesProvider in this case is just a reader which provides value of the field
 * ParserService.INSTANCE.NAMES.
 *
 */
public interface ValuesProvider {

  Class<? extends ValuesProvider> NULL = ValuesProvider.class;

  String[] values();
}

