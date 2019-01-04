# what is this

this is a hive sql format tool

suport:  create select insert

# how to build

   mvn package

# how to use

    1. download release
    2. chmod +x ./hsf
    3. ./hsf ./test.sql


# other

    This code implements two formatting methods.

    1. use my way

    java -classpath hsqlformat-0.0.1-SNAPSHOT.jar  whomm.hsqlformat.App ../bin/test.sql

    2. use alibaba druid

    java -classpath hsqlformat-0.0.1-SNAPSHOT.jar  whomm.hsqlformat.TheApp ../bin/test.sql


