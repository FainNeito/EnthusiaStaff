plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "moderation-platform-api"
        }
    }
}
