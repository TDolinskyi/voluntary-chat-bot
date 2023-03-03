package de.bot

import com.google.inject.AbstractModule
import de.bot.controllers.ControllerModule
import de.bot.db.DBModule
import de.bot.services.ServiceModule

class MainModule extends AbstractModule {
  override def configure() = {
    install(new DBModule())
    install(new SilhouetteModule())
    install(new ServiceModule())
    install(new play.api.libs.mailer.MailerModule())
    //    install(new ControllerModule())
  }
}
