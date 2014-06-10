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

## Jenkins Pipelines

### Building

### Publishing

### Acceptence tests

