architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

loom {
    accessWidenerPath.set(file("src/main/resources/partyapi.accesswidener"))
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    // Remove the next line if you don't want to depend on the API
    modApi("dev.architectury:architectury:${rootProject.property("architectury_version")}")

    compileOnly("net.luckperms:api:5.4")

    implementation("net.kyori:adventure-api:${property("minimessage_version")}")
    implementation("net.kyori:adventure-text-minimessage:${property("minimessage_version")}")
    implementation("net.kyori:adventure-text-serializer-gson:${property("minimessage_version")}")

    implementation("org.mongodb:mongodb-driver-core:${property("mongo_version")}")
    implementation("org.mongodb:mongodb-driver-sync:${property("mongo_version")}")
    implementation("org.mongodb:bson-kotlin:${property("mongo_version")}")

    modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("fabric_kotlin_version")}")
    implementation("redis.clients:jedis:5.1.0")

}