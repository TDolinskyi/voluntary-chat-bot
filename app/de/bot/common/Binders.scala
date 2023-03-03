package de.bot.common

import play.api.mvc.PathBindable

import java.util.UUID

/**
 * Some route binders.
 */
object Binders {

  /**
   * A `java.util.UUID` bindable.
   */
  implicit object UUIDPathBindable extends PathBindable[UUID] {
    def bind(key: String, value: String) = try {
      Right(UUID.fromString(value))
    } catch {
      case _: Exception => Left("Cannot parse parameter '" + key + "' with value '" + value + "' as UUID")
    }

    def unbind(key: String, value: UUID): String = value.toString
  }
}
