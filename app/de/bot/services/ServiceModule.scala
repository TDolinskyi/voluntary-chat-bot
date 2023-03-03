package de.bot.services

import com.google.inject.AbstractModule
import org.slf4j.LoggerFactory

class ServiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ChatBot]).asEagerSingleton()
    bind(classOf[BotMessagesService]).asEagerSingleton()
    bind(classOf[MessageService]).asEagerSingleton()
    bind(classOf[UserService]).asEagerSingleton()
    bind(classOf[AuthTokenService]).asEagerSingleton()
  }

  private val logger = LoggerFactory.getLogger(getClass)

  logger.info("Services module started")

}
