#!/bin/bash

java -classpath .:jade/lib/jade.jar jade.Boot  -gui  -name MiniProject -containers -agents Broker1:BrokerAgent
