package de.bot.controllers

import com.google.inject.AbstractModule

class ControllerModule extends AbstractModule{

  override def configure(): Unit = {
    bind(classOf[MessageController]).asEagerSingleton()
//    bind(classOf[ActivateAccountController]).asEagerSingleton()
//    bind(classOf[ChangePasswordController]).asEagerSingleton()
//    bind(classOf[ForgotPasswordController]).asEagerSingleton()
//    bind(classOf[ResetPasswordController]).asEagerSingleton()
//    bind(classOf[SignUpController]).asEagerSingleton()
//    bind(classOf[SocialAuthController]).asEagerSingleton()
//    bind(classOf[TotpController]).asEagerSingleton()
//    bind(classOf[TotpRecoveryController]).asEagerSingleton()
//    bind(classOf[SignInController]).asEagerSingleton()
  }



}
