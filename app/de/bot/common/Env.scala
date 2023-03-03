package de.bot.common
import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import de.bot.db.model.User

trait DefaultEnv extends Env {
  type I = User
  type A = CookieAuthenticator
}
