plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.ads.domain)
    implementation(projects.dev.feature.ads.data)
    // PostAdViewModel — ObserveCurrentUserUseCase (joriy foydalanuvchi id'si).
    implementation(projects.dev.core.domain)
    implementation(projects.dev.core.database)
    implementation(projects.dev.core.network)
} } }
