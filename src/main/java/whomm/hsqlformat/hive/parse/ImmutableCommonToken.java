package whomm.hsqlformat.hive.parse;


import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

/**
 * This class is designed to hold "constant" CommonTokens, that have fixed type
 * and text, and everything else equal to zero. They can therefore be reused
 * to save memory. However, to support reuse (canonicalization) we need to
 * implement the proper hashCode() and equals() methods.
 */
class ImmutableCommonToken extends CommonToken {

  private static final String SETTERS_DISABLED = "All setter methods are intentionally disabled";

  private final int hashCode;

  ImmutableCommonToken(int type, String text) {
    super(type, text);
    hashCode = calculateHash();
  }

  private int calculateHash() {
    return type * 31 + (text != null ? text.hashCode() : 0);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ImmutableCommonToken)) {
      return false;
    }
    ImmutableCommonToken otherToken = (ImmutableCommonToken) other;
    return type == otherToken.type &&
        ((text == null && otherToken.text == null) ||
          text != null && text.equals(otherToken.text));
  }

  @Override
  public int hashCode() { return hashCode; }

  // All the setter methods are overridden to throw exception, to prevent accidental
  // attempts to modify data fields that should be immutable.

  @Override
  public void setLine(int line) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setText(String text) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setCharPositionInLine(int charPositionInLine) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setChannel(int channel) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setType(int type) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setStartIndex(int start) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setStopIndex(int stop) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setTokenIndex(int index) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }

  @Override
  public void setInputStream(CharStream input) {
    throw new UnsupportedOperationException(SETTERS_DISABLED);
  }
}