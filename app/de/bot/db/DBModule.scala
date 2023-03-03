package de.bot.db

import com.google.inject.AbstractModule
import de.bot.db.common.{MongoAccess, MongoAccessHolder}
import de.bot.db.dao.{AuthTokenDao, MessageDao, UserDao}
import net.codingwell.scalaguice.ScalaModule

class DBModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind(classOf[MongoAccess]).asEagerSingleton()
    bind(classOf[MongoAccessHolder]).asEagerSingleton()
    bind(classOf[MessageDao]).asEagerSingleton()
    bind(classOf[AuthTokenDao]).asEagerSingleton()
    bind(classOf[UserDao]).asEagerSingleton()
  }
}
