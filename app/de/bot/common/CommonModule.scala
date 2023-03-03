package de.bot.common

import com.google.inject.AbstractModule
import de.bot.controllers.MessageController

class CommonModule extends AbstractModule{

  override def configure(): Unit = {
    bind(classOf[CustomUnsecuredErrorHandler]).asEagerSingleton()
    bind(classOf[CustomUnsecuredErrorHandler]).asEagerSingleton()
  }

}
