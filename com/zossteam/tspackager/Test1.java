package com.zossteam.tspackager;

import java.io.IOException;
import java.util.*;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;


import com.zossteam.tspackager.grammar.*;

public class Test1 {

  static class ImportedSymbol {
    Token token;

    
  }

  public static void main(String[] args) throws Exception {
    try {
      JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromFileName(args[0])); // other test mode is ".fromString(<s>)"
      CommonTokenStream stream = new CommonTokenStream(lexer);
      Vocabulary vocabulary = lexer.getVocabulary();

      stream.fill();
      List<Token> tokens = stream.getTokens();
      for (Token token: tokens){
        if (token.getChannel() != Token.HIDDEN_CHANNEL){
          System.out.printf("Token %s: %s\n",token,vocabulary.getSymbolicName(token.getType()));
        }
      }
    } catch (IOException e){
      System.out.printf("probably couldn't read file\n");
    } catch (RecognitionException e){
      e.printStackTrace();
    }
  }
}
