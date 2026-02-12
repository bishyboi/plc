plugins {
    id("java")
}

group = "plc.exercise.regextesting"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

tasks.test {
    useJUnitPlatform()
}
