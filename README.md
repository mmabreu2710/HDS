# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Configuration Files

### Node configuration

For the servers it can be found inside the `resources/` folder of the `Service` module.

For the clients it can be found inside the `resources/` folder of the `Client` module.

```json
{
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
}
```

## Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes
- `client_config` - a string from the array `client_configs` which contains the possible configurations for the client node



Run the script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Run without arguments

```
cd <module>/
mvn compile exec:java
```

Run with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```
---
This codebase was adapted from last year's project solution, which was kindly provided by the following group: [David Belchior](https://github.com/DavidAkaFunky), [Diogo Santos](https://github.com/DiogoSantoss), [Vasco Correia](https://github.com/Vaascoo). We thank all the group members for sharing their code.

### Communication between Clients and Servers

To perform the operations of Check balance and Transfer the format required is the following:

Check balance of node "x":

```
b x
```

Transfer "y" amount to node "x":

```
t x y 
```

The cost of the transfer operation is 1, by default all nodes start with a balance of 100. After rerunning the source code the system returns to its default state.

### Testing

Testing is done by observing the workings of the system when modified to obey test principles.

In the Service and Client module, in NodeService.java (between lines 89 and 95) and in ClientService.java (line 42) respectively there are various boolean variables that control which test is performed. When in state "false" the test is not conducted. To run a specific test, the variable corresponding to the test needs to be changed in the source code to "true". Only a test at a time should be performed. There can not be two tests' variables at state "true" simultaneously. 

All the following tests use Byzantine behaviour:

Test 1:  Verification of correct Check Balance Amount even after interference from a Byzantine Leader

Test 2:  Verification of correct Check Balance Amount even after interference from a non-leader Byzantine Server

Test 3:  Verification of correct Transfer Amount even after interference from a Byzantine Leader

Test 4:  Verification of correct Transfer Amount even after interference from a non-leader Byzantine Server

Test 5:  Verification of correct Transfer Recipient even after interference from a Byzantine Leader

Test 6:  Verification of correct Transfer Recipient after interference from a non-leader Byzantine Server

Test 7:  Server leader sends message with wrong round during round change

Test 8:  Verification of correct Transfer Source after interference from a Byzantine Client

# SEC

