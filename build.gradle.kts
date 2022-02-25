plugins {
    kotlin("jvm") version "1.6.10"
}

group = "com.grappenmaker"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.2")
}

tasks.jar {
    from(configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it) })
    manifest {
        attributes("Main-Class" to "com.grappenmaker.brainfuck.Brainfuck")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}