# Code Pulse

Code Pulse is a code coverage tool that works on running programs in real time. It uses the [bytefrog](https://github.com/secdec/bytefrog) tracing framework to monitor your Java applications, and displays its findings on a web-based UI.

##Layout

**codepulse/** Contains the web app source.

**distrib/** Contains files that are used to package up the entirity of Code Pulse into a native app, using [node-webkit](https://github.com/rogerwang/node-webkit) in place of a browser, and [jetty](http://www.eclipse.org/jetty/) to run the server. *Note: many of the necessary files are not included in this repository, and must be added manually. instructions for this are 'TODO'. If possible, there might eventually be a script to take care of that for you.*

**project/** Contains the SBT build definition.

##Setup

 - Run SBT from this directory. Run `container:start` to start the webserver on [localhost:8080](http://localhost:8080)
 - Eclipse Users:
  - Within SBT, run `eclipse` to generate Eclipse project files.
  - In Eclipse, import the generated projects to your workspace.

##License

Code Pulse is made available under the terms of the Apache License 2.0. See the LICENSE file that accompanies this distribution for the full text of the license.
