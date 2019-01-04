package whomm.hsqlformat.hive.parse;


import org.antlr.runtime.BaseRecognizer;
import org.antlr.runtime.RecognitionException;

/*
 * SemanticException.java
 *
 * Created on April 1, 2008, 1:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

/**
 *
 */
public class ParseError {
  private final BaseRecognizer br;
  private final RecognitionException re;
  private final String[] tokenNames;

  ParseError(BaseRecognizer br, RecognitionException re, String[] tokenNames) {
    this.br = br;
    this.re = re;
    this.tokenNames = tokenNames;
  }

  BaseRecognizer getBaseRecognizer() {
    return br;
  }

  RecognitionException getRecognitionException() {
    return re;
  }

  String[] getTokenNames() {
    return tokenNames;
  }

  String getMessage() {
    return br.getErrorHeader(re) + " " + br.getErrorMessage(re, tokenNames);
  }

}
