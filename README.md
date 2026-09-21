# Minecraft Artifacts Downloader

A Gradle plugin that downloads Minecraft artifacts based on version and environment.

## Requirements
- Gradle 7.6+
- Java 8+

## Installation

Apply the plugin in your `build.gradle`:

```groovy
plugins {
    id 'io.github.freehij.mcartifact' version '1.0.0'
}

mcartifact {
    minecraftVersion = '26.1.2'
    environment = 'client'
}
```

## Version range

List of versions can be seen here https://github.com/freehij/resources/blob/main/versions.json