package de.bot.db.common

object BotExceptions {

  case class DaoException(msg: String) extends Exception

}
