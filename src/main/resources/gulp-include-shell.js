/*global process, require */

(function() {

  "use strict";

  var args = process.argv,
      fs = require("fs"),
      Q = require('q'),
      nodefn = require("when/node"),
      mkdirp = require("mkdirp"),
      path = require("path"),
      gutil = require("gulp-util"),
      include = require("gulp-include");

  var promised = {
    mkdirp: nodefn.lift(mkdirp),
    readFile: nodefn.lift(fs.readFile),
    writeFile: nodefn.lift(fs.writeFile)
  };

  var SOURCE_FILE_MAPPINGS_ARG = 2;
  var TARGET_ARG = 3;
  var OPTIONS_ARG = 4;

  var sourceFileMappings = JSON.parse(args[SOURCE_FILE_MAPPINGS_ARG]);
  var target = args[TARGET_ARG];

  var sourcesToProcess = sourceFileMappings.length;
  var results = [];
  var problems = [];

  function compileDone() {
    if (--sourcesToProcess == 0) {
      console.log("\u0010" + JSON.stringify({results: results, problems: problems}));
    }
  }

  function isProblem(result) {
    return result.message !== undefined &&
      result.severity !== undefined &&
      result.lineNumber !== undefined &&
      result.characterOffset !== undefined &&
      result.lineContent !== undefined &&
      result.source !== undefined;
  }

  function parseError(input, contents, err) {
    return {
      message: err.message,
      severity: "error",
      lineNumber: 0,
      characterOffset: 0,
      lineContent: "Unknown line",
      source: input
    };
  }

  sourceFileMappings.forEach(function(sourceFileMapping) {
    var input = sourceFileMapping[0];
    var output = path.join(target, sourceFileMapping[1]);

    promised.readFile(input, "utf8").then(function(contents) {
      
      var d = Q.defer();  
      
      var file = new gutil.File({
          base: path.dirname(input),
          path: input,
          contents: fs.readFileSync(input)
      });

      var gulpInclude = include();
      gulpInclude.on("data", function (newFile) {
          d.resolve({html: newFile.contents})
      });
      gulpInclude.write(file);

      return d.promise;
    }).then(function(result) {
      return promised.mkdirp(path.dirname(output)).yield(result);
    }).then(function(result) {
      return promised.writeFile(output, result.html, "utf8").yield(result);
    }).then(function(result) {
      results.push({
        source: input,
        result: {
          filesRead: [input],
          filesWritten: [output]
        }
      });
    }).catch (function(e) {
      if (isProblem(e)) {
        problems.push(e);
        results.push({
          source: input,
          result: null
        });
      } else {
        console.error(e);
      }
    }).finally(function() {
       compileDone();
    });
  });

}).call(this);