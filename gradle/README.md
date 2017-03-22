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

### Local Maven repository
To publish artifacts into **local Maven** repository type:

```
gradle install
```

### Local build repository
To publish artifacts into **local Maven** repository stored under `build/repo`:

```
gradle publish
```

### Sonatype Release Repository
To publish artifacts into remote **Sonatype Release** repository please type:
```
gradle -DdoRelease publish
```

#### Sonatype release repository configuration

To upload artifacts into remote repository the file `~/.gradle/gradle.properties` has to contains your Sonatype credentials
```
oss-releases.username=<your Sonatype username>
oss-releases.password=<your Sonatype password>
```

Sonatype release repository requires signed artifacts.
Hence it is necessary to provide GNUPG key reference into`~/.gradle/gradle.properties` or via command line correspond `-P` options:

```
signing.keyId=<Your Key Id>
signing.password=<Your Public Key Password>
signing.secretKeyRingFile=<Path To Your Key Ring File>
```

To import H2O public key, please use:
```
gpg2 --keyserver hkp://pool.sks-keyservers.net --recv-keys 539BAEFF
```

The published artifacts are available at https://oss.sonatype.org. 
They need manual approval and propagation to maven central. 
Please use OSS credentials to log into OSS (http://oss.sonatype.org/), select _Staging Repositories_ item, select `ai.h2o` repository (verify open date), then close the repository and propagate into Maven Cenral.

The artifacts should be available via Maven central in a few minutes.
To check them please use the following search link: http://search.maven.org/#search%7Cga%7C1%7Cai.h2o

#### Automatic release and propagation into Maven Central
To release, approve and propagate H2O artifacts into Maven central automatically please type:
```
gradle -PdoRelease release
``` 

> The `release` task has the same requirements as `publish` task described above


## Jenkins Pipelines
In release Jenkins job, please setup environment and call the `make-dist.sh` script.


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
    `./gradlew -PdoFindbugs=true install`

