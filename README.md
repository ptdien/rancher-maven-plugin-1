#Rancher API Maven plugin [![Build Status](https://travis-ci.org/RedFroggy/rancher-maven-plugin.svg?branch=master)](https://travis-ci.org/RedFroggy/rancher-maven-plugin)

A Maven plugin for interacting with [rancher](http://rancher.com).

##Goal
There is only one goal: stack-deploy which purpose is to delete and/or create 
a new rancher stack thanks to a docker-compose file

##Usage
###pom.xml file
```
<plugin>
    <groupId>fr.redfroggy.plugins</groupId>
    <artifactId>rancher</artifactId>
    <version>1.0.0</version>
    <configuration>
        <dockerComposeFile>src/main/resources/docker-compose.yml</dockerComposeFile>
        <accessKey>${ACCESS_KEY}</accessKey>
        <password>${PASSWORD}</password>
        <url>http://localhost:8082/v2-beta</url>
        <name>registry</name>
        <description>Registry Server</description>
        <environment>dev</environment>
    </configuration>
</plugin>
```
###Command line
All optons can be overidden by using line arguments:
```
- rancher.accessKey #rancher login
- rancher.password # rancher password
- rancher.url #rancher url (should be http://HOST/v2-beta)
- rancher.environment # environment
- rancher.stack.name #Name of the stack to delete/create
- rancher.stack.description #Stack description
- rancher.stack.startOnCreate
- rancher.stack.dockerComposeFilePath #docker-compose filepath
- rancher.stack.rancherComposeFilePath #rancher-compose filepath
```

Examples:
```
mvn rancher:stack-deploy -Drancher.accessKey=XXXX -Drancher.password=YYYYY
```

##Tests
```
mvn clean test
```