package dev.kpp.analyzer

import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FILE
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER

// SOURCE retention because the markers exist for the static scanner only;
// they have no runtime semantics in the MVP.

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER)
annotation class MustHandle

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER, CLASS, FILE)
annotation class Pure

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER, CLASS, FILE)
annotation class Io

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER, CLASS, FILE)
annotation class Db

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER, CLASS, FILE)
annotation class Blocking

@Retention(SOURCE)
@Target(FUNCTION, PROPERTY_GETTER, CLASS, FILE)
annotation class PublicApi
