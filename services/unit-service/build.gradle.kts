dependencies {
    implementation(libs.koin.ktor)
    implementation(libs.ktor.server.feature.resources)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hibernateValidator)
    implementation(projects.configuration)
    implementation(projects.services.idService)
    implementation(projects.services.unitInstanceService)
    implementation(projects.services.idService)
    implementation(projects.http.httpShared)
    testImplementation(projects.http.httpTest)
}
