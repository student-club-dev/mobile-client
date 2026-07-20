plugins { id("sc.module-data") }
kotlin { sourceSets { commonMain.dependencies { api(projects.dev.feature.settings.domain) } } }
