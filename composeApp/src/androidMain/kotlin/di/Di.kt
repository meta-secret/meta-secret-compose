package di

import db.AppDatabase
import db.entities.getDatabase
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AppDatabase> { getDatabase(get()) }
}