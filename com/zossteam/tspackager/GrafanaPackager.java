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

public class GrafanaPackager extends PackagerBase {

  static String grafanaPluginTrailer =
"\n"+
"  return (function (e) { // receives the array of 1 functions below \n"+
"        var t = {};\n"+
"        function n(a) {\n"+
"            if (t[a]) return t[a].exports;\n"+
"            var o = (t[a] = { i: a, l: false, exports: {} });\n"+
"	    console.log(\"SIMPLE in n(a), e=\");\n"+
"	    console.log(e);\n"+
"	    e[a].call(o.exports, o, o.exports, n);\n"+
"	    o.l = true;\n"+
"            return o.exports;\n"+
"        }\n"+
"        return (\n"+
"            (n.d = function (e, t, a) {\n"+
"                n.o(e, t) || Object.defineProperty(e, t, { enumerable: true, get: a });\n"+
"            }),\n"+
"            (n.r = function (e) {\n"+
"                \"undefined\" != typeof Symbol && Symbol.toStringTag && Object.defineProperty(e, Symbol.toStringTag, { value: \"Module\" }), Object.defineProperty(e, \"__esModule\", { value: !0 });\n"+
"            }),\n"+
"            (n.o = function (e, t) {\n"+
"                return Object.prototype.hasOwnProperty.call(e, t);\n"+
"            }),\n"+
"            n((n.s = 0))\n"+
"        );\n"+
"    })([\n"+
"        function (e, t, n) {\n"+
"	    var v = makeGrafanaPlugin();\n"+
"	    n.d(t, \"plugin\", function () {\n"+
"                return v;\n"+
"            });\n"+
"        }\n"+
"    ]);\n"+
    "});\n";


  GrafanaPackager(String rootName){
    super(rootName);
  }

  static boolean isWindows = System.getProperty("os.name").startsWith("Windows");


  public static void main(String[] args) throws Exception {
    String outputFileName = args[0];
    GrafanaPackager packager = new GrafanaPackager(args[1]);
    for (int i=2; i<args.length; i++){
      packager.addModule(args[i]);
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    packager.validateUniqueSymbol("makeGrafanaPlugin");
    packager.validateIntramoduleImports();
    List<ImportedModule> externalModules = packager.getExternalModules();
    out.printf("define([");
    int m;
    for (m=0; m<externalModules.size(); m++){
      ImportedModule externalModule = externalModules.get(m);
      if (m > 0){
        out.printf(", ");
      }
      out.printf("\"%s\"",externalModule.getName());
    }
    out.printf("], function(");
    for (m=0; m<externalModules.size(); m++){
      ImportedModule externalModule = externalModules.get(m);
      if (m > 0){
        out.printf(", ");
      }
      String substituteName = externalModule.getLocalSubstituteName();
      out.printf("%s",substituteName);
    }
    out.printf("){%s\n",isWindows? "\r" : "");
    packager.hideExports = true;
    packager.writeTransformedModules(out);
    if (isWindows){
      out.printf(grafanaPluginTrailer.replace("\n","\r\n"));
    } else {
      out.printf(grafanaPluginTrailer);
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

