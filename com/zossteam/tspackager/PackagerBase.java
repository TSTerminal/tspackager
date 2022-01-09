package com.zossteam.tspackager;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;

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

/*
  The packager assumes a valid es6 or later javascript module.

  
 */

public class PackagerBase {
  ArrayList<SourceModule> sourceModules = new ArrayList();
  Path root;
  HashSet<Token> tokensInImport = new HashSet();
  HashSet<Token> tokensInExport = new HashSet();
  protected boolean hideExports = false;
  protected boolean onlyExportManually = false;
  protected boolean trace = false;
  
  PackagerBase(String rootName){
    this.root = Paths.get(rootName);
    if (!Files.exists(this.root)){
      throw new IllegalArgumentException("packaging root dir does not exist: "+rootName);
    }
  }

  protected SourceModule addModule(String filename) throws IOException {
    SourceModule module = new SourceModule(filename);
    sourceModules.add(module);
    processImports(module);
    return module;
  }

  class SourceModule {
    String filename;
    JavaScriptLexer lexer;
    Vocabulary vocabulary;
    List<Token> tokens;

    HashMap<String,ImportedModule> importedModules = new HashMap();
    HashMap<String,ImportedSymbol> importedSymbols = new HashMap();
    
    SourceModule(String filename) throws IOException {
      Path path = Paths.get(root.toString(),filename);
      this.filename = filename;
      this.lexer = new JavaScriptLexer(CharStreams.fromPath(path));
      CommonTokenStream stream = new CommonTokenStream(lexer);
      stream.fill();
      this.vocabulary = this.lexer.getVocabulary();
      this.tokens = stream.getTokens();
    }

    void dumpImports(){
      for (ImportedModule module: importedModules.values()){
        System.out.printf("Imports from module %s will be using localPrefix '%s.'\n",
                          module.name,module.substituteIdentifier);
      }
    }

    ImportedModule getOrMakeImportedModule(String moduleName){
      ImportedModule module = importedModules.get(moduleName);
      if (module == null){
        module = new ImportedModule(moduleName);
        importedModules.put(moduleName,module);
      }
      return module;
    }

  }

  protected class ImportedModule {
    String name;
    String substituteIdentifier;
    boolean isInternal = false;
    ArrayList<ImportedSymbol> symbols = new ArrayList();

    ImportedModule(String name){
      this.name = name;
      // eventually make substitute
      char[] chars = name.toCharArray();
      StringBuilder builder = new StringBuilder();
      boolean lastWasNotAlphanumeric = false;
      for (char c: chars){
        if (Character.isAlphabetic(c) || Character.isDigit(c)){
          if (lastWasNotAlphanumeric){
            builder.append(Character.toUpperCase(c));
          } else {
            builder.append(c);
          }
          lastWasNotAlphanumeric = false;
        } else {
          lastWasNotAlphanumeric = true;
        }
      }
      substituteIdentifier = builder.toString();
    }

    void addImports(SourceModule sourceModule, List<ImportedSymbol> imports){
      for (ImportedSymbol symbol: imports){
        symbols.add(symbol);
        symbol.module = this;
        ImportedSymbol existingSymbol = sourceModule.importedSymbols.get(symbol.name);
        if (existingSymbol != null){
          throw new IllegalStateException("double import of symbol: "+symbol.name);
        } else {
          sourceModule.importedSymbols.put(symbol.name,symbol);
        }
      }
    }

    public String getName(){
      return name;
    }

    public String getLocalSubstituteName(){
      return substituteIdentifier;
    }

  }



  protected static class ImportedSymbol {
    Token token;
    String name; // really only for identifiers
    boolean isDefaultExport = false;
    ImportedModule module;
    Token aliasToken;

    ImportedSymbol(Token token){
      this.token = token;
      this.name = token.getText();
    }

  }


  private final int INITIAL = 0;
  private final int EXPECTING_SYMBOL_SPEC = 1; // LBRACE or * or identifier
  private final int EXPECTING_GROUP_SYMBOL = 2;
  private final int AFTER_GROUP_SYMBOL = 3; // COMMA, 'AS' or RBRACE
  private final int EXPECTING_MODULE_NAME = 4;
  private final int AFTER_DEFAULT_EXPORT = 5;
  private final int AFTER_MODULE_NAME = 6;
  private final int IN_EXPORT = 7;

  static String[] stateNames = { "initial", "expect_sym_spec", "expect_group_sym", "after_group_sym", "expect_module",
                                 "after_default_export", "after_module_name", "in_export"};

  void parseException(String message, Token token){
    throw new IllegalStateException(message+" near: "+token);
  }

  void processImports(SourceModule sourceModule){
    int state = INITIAL;
    ImportedSymbol currentImport = null;
    ArrayList<ImportedSymbol> currentImports = null;
    boolean inImport = false;
    for (Token token: sourceModule.tokens){
      int type = token.getType();
      if (token.getChannel() == Token.HIDDEN_CHANNEL){
        continue;
      }
      if (inImport){
        tokensInImport.add(token);
      }
      // System.out.printf("process (state = %s) %s\n",stateNames[state],token);
      switch (state){
      case INITIAL:
        // System.out.printf("type = %d\n",type);
        switch (type){
        case JavaScriptLexer.Import:
          System.out.printf("JOE IMPORT TOKEN\n");
          state = EXPECTING_SYMBOL_SPEC;
          currentImports = new ArrayList();
          tokensInImport.add(token);
          inImport = true;
          break;
        case JavaScriptLexer.Export:
          state = IN_EXPORT;
          tokensInExport.add(token);
          break;
        default:
          // do nothing cuz we are not in an import
        }
        break;
      case IN_EXPORT:
        tokensInExport.add(token);
        switch (type){
        case JavaScriptLexer.SemiColon:
          state = INITIAL;
          break;
        default:
          // do nothing
          break;
        }
        break;
      case EXPECTING_SYMBOL_SPEC:
        switch (type){
        case JavaScriptLexer.OpenBrace:
          state = EXPECTING_GROUP_SYMBOL;
          break;
        case JavaScriptLexer.Identifier:
          currentImport = new ImportedSymbol(token);
          currentImport.isDefaultExport = true;
          currentImports.add(currentImport);
          state = AFTER_DEFAULT_EXPORT;
          break;
        case JavaScriptLexer.From:
          state = EXPECTING_MODULE_NAME;
          break;
        default:
          parseException("unexpected import syntax 1",token);
        }
        break;
      case EXPECTING_GROUP_SYMBOL:
        switch (type){
        case JavaScriptLexer.Identifier:
          System.out.printf("JOE IMPORT SYM %s\n",token);
          currentImport = new ImportedSymbol(token);
          currentImports.add(currentImport);
          state = AFTER_GROUP_SYMBOL;
          break;
        default:
          parseException("unexpected import syntax 2",token);
        }
        break;
      case AFTER_GROUP_SYMBOL:
        switch (type){
        case JavaScriptLexer.Comma:
          state = EXPECTING_GROUP_SYMBOL;
          break;
        case JavaScriptLexer.CloseBrace:
          state = EXPECTING_SYMBOL_SPEC;
          break;
        case JavaScriptLexer.As:
          parseException("import <sym> as ... not yet supported",token);
        default:
          parseException("unexpected import syntax 3",token);
        }
        break;
      case AFTER_DEFAULT_EXPORT:
        switch (type){
        case JavaScriptLexer.Comma:
          state = EXPECTING_SYMBOL_SPEC;
          break;
        case JavaScriptLexer.From:
          state = EXPECTING_MODULE_NAME;
          break;
        default:
          parseException("unexpected import syntax 4",token);
        }
        break;
      case EXPECTING_MODULE_NAME:
        switch(type){
        case JavaScriptLexer.StringLiteral:
          {
            if (currentImports == null || currentImports.size() == 0){
              parseException("empty import seen",token);
            }
            String moduleName = token.getText();
            moduleName = moduleName.substring(1,moduleName.length()-1); // get rid of quotes
            ImportedModule module = sourceModule.getOrMakeImportedModule(moduleName);
            module.addImports(sourceModule,currentImports);
            state = AFTER_MODULE_NAME;
          }
          break;
        default:
          parseException("unexpected import syntax 5",token);
        }
        break;
      case AFTER_MODULE_NAME:
        switch (type){
        case JavaScriptLexer.SemiColon:
          state = INITIAL;
          inImport = false;
          break;
        default:
          parseException("unexpected import syntax 6",token);
          break;
        }
        break;
      default:
        parseException("unhandled state "+state,token);
      }
    }
  }
  
  void validateUniqueSymbol(String identifierName){
    int count = 0;
    for (SourceModule module: sourceModules){
      for (Token token: module.tokens){
        if ((token.getType() == JavaScriptLexer.Identifier) &&
            (token.getText().equals(identifierName))){
          count++;
          if (count > 1){
            throw new IllegalStateException(String.format("'%s' mentioned twice one is %s",identifierName,token));
          }
        }
      }
    }
    if (count == 0){
      throw new IllegalStateException(String.format("'%s' not defined in any source module",identifierName));
    }
  }

  static HashSet<String> wellKnownExternalModules = new HashSet();

  static {
    wellKnownExternalModules.add("react");
    wellKnownExternalModules.add("emotion");
  }

  void validateIntramoduleImports(){
    for (SourceModule sourceModule: sourceModules){
      for (ImportedModule importedModule: sourceModule.importedModules.values()){
        String name = importedModule.name;
        if (name.startsWith(".")){
          importedModule.isInternal = true;
        } else if (name.startsWith("@")){
          importedModule.isInternal = false;
        } else if (Character.isAlphabetic(name.charAt(0))){
          // could be either
          if (wellKnownExternalModules.contains(name)){
            importedModule.isInternal = false;
          } else {
            importedModule.isInternal = true;
          }
          /* hack!! */
          if (importedModule.name.equals("react")){
            importedModule.substituteIdentifier = "React";
          }
        } else {
          System.out.printf("%s startsWith %s\n",name,name.startsWith("@"));
          throw new IllegalStateException("unexpected module name syntax: "+name);
        }
      }
    }
  }

  List<ImportedModule> getExternalModules(){
    HashMap<String,ImportedModule> externalModules = new HashMap();
    for (SourceModule sourceModule: sourceModules){
      for (ImportedModule importedModule: sourceModule.importedModules.values()){
        if (!importedModule.isInternal){
          externalModules.put(importedModule.name,importedModule);
        }
      }
    }
    ArrayList<ImportedModule> result = new ArrayList();
    result.addAll(externalModules.values());
    return result;
  }

  
  
  void writeTransformedModules(PrintStream out){
    for (SourceModule module: sourceModules){
      writeTransformedModule(out,module);
      out.printf("\n"); // ensure modules don't output on same line
    }
  }

  void writeTransformedModule(PrintStream out, SourceModule sourceModule){
    for (int i=0; i<sourceModule.tokens.size(); i++){
      Token token = sourceModule.tokens.get(i);
      if (tokensInImport.contains(token)){
        // do nothing
      } else if (hideExports && tokensInExport.contains(token)){
        if (trace){
          System.out.printf("hiding export %s\n",token);
        }
        // do nothing
      } else if (onlyExportManually && token.getText().equals("export")){
        if (trace){
          System.out.printf("hiding the word 'export' %s\n",token);
        }
      } else {
        if (token.getType() == JavaScriptLexer.Identifier){
          String id = token.getText();
          ImportedSymbol importedSymbol = sourceModule.importedSymbols.get(id);
          if (importedSymbol != null &&
              !importedSymbol.isDefaultExport &&
              !importedSymbol.module.isInternal){
            out.printf("%s.%s",importedSymbol.module.substituteIdentifier,id);
          } else {
            out.printf("%s",id);
          }
        } else {
          out.printf("%s",token.getText());
        }
      }
    }
  }

  // resolve test
  public static void main2(String[] args) throws Exception {
    Path basePath = Paths.get(args[0]);
    Path resolvedPath = basePath.resolve(args[1]);

    System.out.printf("resolves to '%s', exists? %s\n",resolvedPath.normalize(),Files.exists(resolvedPath));
  }

  public static void main(String[] args) throws Exception {
    // JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromFileName(args[0])); // other test mode is ".fromString(<s>)"
    PackagerBase packager = new PackagerBase(args[0]);
    SourceModule module = packager.addModule(args[1]);
    module.dumpImports();
    packager.writeTransformedModule(System.out,module);
    
  }

}
