# Bytefrog

Bytefrog is an execution tracing framework for the Java Virtual Machine. It consists of a Java agent to perform bytecode instrumentation and perform the actual tracing and a receiver component for collecting and processing the trace data.

More documentation on Bytefrog is available in the [wiki](//github.com/secdec/bytefrog/wiki).

<sub>[![Build Status](https://travis-ci.org/secdec/bytefrog.svg?branch=master)](https://travis-ci.org/secdec/bytefrog)</sub> (master branch)

## Layout

**agent/**
Contains the project contents for the Java Agent. This is java-only, and since it is what gets loaded into processes being traced, we don't want to include any 3rd-party libraries if we don't have to. The majority of this code handles bytecode instrumentation and shuttling trace data upstream.

**common/**
Contains the project contents for the common library between the Java Agent and "Headquarters". This is also be java-only, as it will be included in the packaged agent. The majority of this code will deals with the communication protocols between the Agent and HQ.

**hq/**
Contains the project contents for the "Headquarters". This project depends on "common" and is unrestricted in terms of other dependencies. Primarily written in Scala, this includes the framework for data collection and processing.

**readme.md**
This file.

## Setup

 - Run SBT from this directory. There are projects here for the tracer components. Run `assembly` on the `Agent` project to package up the tracer agent jar. The other projects are libraries meant for consumption by other componenents.
 - Eclipse Users:
   - Within SBT, run `eclipse` to generate Eclipse project files.
   - In Eclipse, import the generated projects to your workspace.

##License

Bytefrog is made available under the terms of the Apache License 2.0. See the LICENSE file that accompanies this distribution for the full text of the license.
