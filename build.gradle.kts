import ca.stellardrift.permissionsex.gradle.spongeRepo
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.ben-manes.versions") version "0.25.0"
    kotlin("jvm") version "1.3.50" apply false
    `maven-publish`
}
allprojects {
    group = "ca.stellardrift.permissionsex"
    version = "2.0-SNAPSHOT"
}

repositories {
    jcenter()
    spongeRepo()
}

subprojects {
    // TODO: Move this into buildSrc? Need to figure out how to get kotlin plugin
    //  into buildSrc classpath so we can work with it directly :(
    apply(plugin="kotlin")

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    dependencies {
        "implementation"(kotlin("stdlib-jdk8"))
    }
}
