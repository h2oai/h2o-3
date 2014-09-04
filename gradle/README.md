# H2O Gradle Build

## Artifacts
Each project can provide artifact.
The Java-based projects provides jar files.

## FatJar

## Publishing
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

## Versioning

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
  * `./gradlew :h2o-test:test`
  
* How can I run gradle daemon by default?
  * Put `org.gradle.daemon=true` into your `~/.gradle/gradle.properties`
  
* How can I run gradle without daemon?
  * `./gradlew --no-daemon`
  
* How can I open h2o-dev project in Idea?
  * Open project via selecting top-level `build.gradle` file.
   
