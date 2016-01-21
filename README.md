RTP Mongo Library - Scala library to work with Mongodb drivers
==============================================================

Application built with the following (main) technologies:

- Scala

- SBT

- Casbah

- Salat

Introduction
------------
TODO

Application
-----------
TODO

Build and Deploy
----------------
The project is built with SBT. On a Mac (sorry everyone else) do:
> brew install sbt

It is also a good idea to install Typesafe Activator (which sits on top of SBT) for when you need to create new projects - it also has some SBT extras, so running an application with Activator instead of SBT can be useful. On Mac do:
> brew install typesafe-activator

To compile:
> sbt compile

or
> activator compile

To run the specs:
> sbt test

The following packages up this library - Note that "assembly" will first compile and test:
> sbt assembly