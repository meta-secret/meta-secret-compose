package di

import db.AppDatabase
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AppDatabase> { getDatabase() }
}