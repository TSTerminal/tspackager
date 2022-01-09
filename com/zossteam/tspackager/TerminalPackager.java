package com.zossteam.tspackager;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

/*
  The packager assumes a valid es6 or later javascript module.

  
 */

public class TerminalPackager extends PackagerBase {


  
  static String terminalTrailer =
"   exports.TerminalLauncher = TerminalLauncher; \n"+
"   exports.CharsetInfo = CharsetInfo; \n"+
"   return exports;\n"+
"})(\"undefined\" == typeof org_zowe_tsterm ? (org_zowe_tsterm = {}) : org_zowe_tsterm);";

  TerminalPackager(String rootName){
    super(rootName);
    this.trace = true;
  }

  static boolean isWindows = System.getProperty("os.name").startsWith("Windows");

  // java com.zossteam.tspackager.TerminalPackager tsterm.js . chardata.js utils.js generic.js paged.js graphics.js model3270.js launcher.js

  public static void main(String[] args) throws Exception {
    String outputFileName = args[0];
    TerminalPackager packager = new TerminalPackager(args[1]);
    for (int i=2; i<args.length; i++){
      packager.addModule(args[i]);
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    packager.validateIntramoduleImports();
    List<ImportedModule> externalModules = packager.getExternalModules();
    out.printf("!(function (exports) {%s\n",isWindows? "\r" : "");
    packager.hideExports = false;
    packager.onlyExportManually = true;
    packager.writeTransformedModules(out);
    if (isWindows){
      out.printf(terminalTrailer.replace("\n","\r\n"));
    } else {
      out.printf(terminalTrailer);
    }
    out.flush();
    byte[] data = baos.toByteArray();
    out.close();
    // The following nasty loop filters window CR LF's down to LF
    BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(outputFileName));
    for (int i=0; i<data.length; i++){
      int b = data[i]&0xff;
      if (b != 13){
        fileOut.write(b);
      }
    }
    fileOut.close();
  }
}

