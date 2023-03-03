package de.bot.common

import play.api.mvc.Call

/**
 * Defines some common redirect calls used in authentication flow.
 */
object Calls {
  /** @return The URL to redirect to when an authentication succeeds. */
  def home: Call = de.bot.controllers.routes.MessageController.index

  /** @return The URL to redirect to when an authentication fails. */
  def signin: Call = de.bot.controllers.routes.SignInController.view
}
