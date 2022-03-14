package org.katan.maestro.di

import org.katan.maestro.Maestro
import org.katan.maestro.MaestroImpl
import org.koin.dsl.module

val MaestroModule = module {
	single<Maestro> { MaestroImpl() }
}