# TSPackager

TSPackage is a packager that can be used to package TypeScript projects for use in Node and WebUI's.   It's an experiment to a make packager that is unlike Webpack, whose only mission is to merge TypeScript output to various loadable formats.    Webpack, Babel etc are oriented towards a world before EcmaScript Modules, Classes, TypeScript.   

## What does it do?

It sews togther a set of javascript modules (output by the TypeScript compiler) that presumably use classes and resolve imports between them.  It then provides some ~~horrible~~ standard  import/export wrapper to accomodate the loading environment.

## Why does it exist?

I wrote it initially to make Grafana Plugins in TypeScript without having to adopt Grafana's extensive build setup.   Like a lot of modern projects, explicit runtime dependencies and modularity rules are not expressed or documented.  Instead wildly complex and cranky development-time dependencies are recommended.  I don't like programming that way, so I just wrote a packager to do what I need and not do 100 things that I don't even want to know about.  I now use it to avoid Webpack entirely for all of my TypeScript work. 

## Dependencies

This package uses Antlr4 for parsing javascript.  It also the JavaScript grammar from the Antlr github examples.  

## Known Limitations

Cyclic dependencies are probably not handled.   I don't write code with cyclic dependencies very often.  

## Future Directions

- A better command line or input file to call out options and choices more explicitly.  

- Explicitly note output module type, eg CJS,UMD,AMD,ES6.   

- This package should move from to TypeScript from java.  I have recently started using the javascript runtime from Antlr in Typescipt projects.  It would be much more natural to drive builds with just Node- and JS-based tooling.  
