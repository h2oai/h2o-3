# H2O Gradle Build

## Artifacts
H2O artifacts are published in [Maven Central](http://search.maven.org).
The artifacts are available via [this query](http://search.maven.org/#search%7Cga%7C1%7Cai.h2o).

  * `h2o-core` - core functionaly of H2O platform including K/V store, M/R framework, networking
  * `h2o-algos` - basic set of algorithms (GLM, GBM, DeepLeaning,...)
  * `h2o-app` - H2O standalone application launcher
  * `h2o-web` - H2O web UI called _Steam_
  * `h2o-scala` - Scala API
  
### Versioning of Artifacts
All published artifacts share the same version enforced by
parent project. See file `gradle.properties` which contains version definition.

## Artifacts Building

## Artifacts Assembling

## Artifacts Publishing
For publishing gradle nexus pluging is used (see
https://github.com/bmuschko/gradle-nexus-plugin).

It publishes artifacts into:

  * Local maven repository
  * Sonatype snapshot repository
  * Sonatype release repository

> To upload artifacts into remote repository the file `~/.gradle/gradle.properties` has to contains your Sonatype credentials
>

>     nexusUsername=<your Sonatype username>
>     nexusPassword=<your Sonatype password>
>

The published artifacts are available at https://oss.sonatype.org.

### Local Maven repository
To publish artifacts into local Maven repository type:

```
gradle install
```

### Sonatype snapshot repository
To publish artifacts into remote Sonatype repository type:
```
gradle uploadArchives
```

### Sonatype release repository
Sonatype release repository requires signed artifacts.
Hence it is necassary to provide GNUPG key reference into`~/.gradle/gradle.properties`:

```
signing.keyId=<Your Key Id>
signing.password=<Your Public Key Password>
signing.secretKeyRingFile=<Path To Your Key Ring File>
```

To import H2O public key, please use:
```
gpg2 --keyserver hkp://pool.sks-keyservers.net --recv-keys 539BAEFF
```

To release H2O artifacts please type:
```
gradle release
``` 

### Maven Central Repository

To publish artifacts to Maven central repository, use instructions for publishing into
Sonatype release repository. 
Then go to http://oss.sonatype.org/ and publish the artifacts.

They should be available via Maven central in a few minutes.
To check them please use the following search link: http://search.maven.org/#search%7Cga%7C1%7Cai.h2o


## Jenkins Pipelines

### Building

### Publishing

### Acceptence tests

## Your ~/.gradle/gradle.properties
If you are Mr. Jenkins or a person responsible for releasing you
`~./.gradle/gradle.properties` file should contain following information:
```
nexusUsername=<your Sonatype username>
nexusPassword=<your Sonatype password>

signing.keyId=<Your Key Id>
signing.password=<Your Public Key Password>
signing.secretKeyRingFile=<Path To Your Key Ring File>
```

## Gradle FAQ

* Should i use `gradle` command from my machine or `gradlew` command provided by the project?
  * Use `gradlew` command since it will download expected (and supported) gradle version for you.

* How can I run a specific task (i.e., test)?
  * `./gradlew test`
  
* How can I run a specific task on a particular project?
  * `./gradlew :h2o-scala:test`
  
* How can I run gradle daemon by default?
  * Put `org.gradle.daemon=true` into your `~/.gradle/gradle.properties`
  
* How can I run gradle without daemon?
  * `./gradlew --no-daemon`
  
* How can I open h2o-dev project in Idea?
  * Open project via selecting top-level `build.gradle` file.
   
* How can I work offline with gradle?
  * Run gradle with `--offline` command line parameter, for example: `./gradlew --offline
    test`

* How can I pass a parameter to gradle build?
  * Specify parameter with `-P` option on gradle command line, for example:
    `./gradlew -Pdisable.java6bytecode.gen=true install`

