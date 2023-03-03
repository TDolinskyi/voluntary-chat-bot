package de.bot.services

import cats.effect._
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.Implicits._
import com.bot4s.telegram.api.AkkaTelegramBot
import com.bot4s.telegram.api.declarative.{Callbacks, Commands, InlineQueries}
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.methods._
import com.bot4s.telegram.models._
import com.google.common.cache.CacheBuilder
import de.bot.db.common.BotExceptions.DaoException
import de.bot.db.dao.MessageDao
import de.bot.db.dao.MessageDao._
import de.bot.db.model._
import de.bot.services.BotMessagesService.cacheMultiLanguage
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Success, Try}

object BotMessagesService {

  val cacheMultiLanguage = CacheBuilder
    .newBuilder()
    .initialCapacity(1000)
    .maximumSize(2000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build[Long, Boolean]()

  case class BotInfoResult(username: Option[String])

  object BotInfoResult {
    implicit val format = Json.format[BotInfoResult]
  }

  case class BotInfo(result: BotInfoResult)

  object BotInfo {
    implicit val format = Json.format[BotInfo]
  }

  object Translation {

    val ua = "UA"
    val en = "EN"

    val unknownError = "ue"
    val unsupportedMessageError = "ume"
    val dateError = "de"
    val dayError = "daye"
    val name = "name"
    val phone = "phone"
    val oneOfTwo = "oot"
    val securityYes = "sy"
    val securityNo = "sn"
    val medicYes = "my"
    val medicNo = "mn"
    val date = "date"
    val days = "days"
    val timeBeforeLunch = "tb"
    val timeAfterLunch = "ta"
    val timeWholeDay = "tw"
    val check = "check"
    val checkNoData = "checkNo"
    val help = "help"
    val language = "lang"
    val yes = "yes"
    val no = "no"
    val blBtn = "blbtn"
    val alBtn = "albtn"
    val wdBtn = "wdBtn"
    val mBtn = "mBtn"
    val nmBtn = "nmBtn"

    val txtMap: Map[String, Map[String, String]] = Map(
      ua -> Map(
        help ->
          s"""Будь ласка напишіть /start щоб отримати більше інформації та почати реєстрацію, у випадку якщо ви вже реєстревались ви можете перевірити свої дані командою /check.""".stripMargin,
        language ->
          s"""Привіт!
             |Цей бот допоможе вам в реєстрацію на волонтерську діяльність в Берліні.
             |
             |Ми проведемо вас крок за кроком по реєстраційній формі. Ці дані будуть надіслані до наших волонтерів в Берліні які звʼяжуться з вами та нададуть інформацію щодо розкладу та позиції.
             |
             |Імʼя - імʼя та за бажанням прізвище;
             |Номер телефону - нам потрібен ваш номер телефону, щоб мати змогу звʼязатися з вами;
             |Чиє у вас медичний досвід? - деякі задачі потребують медичного досвіду, наприклад сортування медикаментів, або медичних приладів
             |Дата початку - дата коли ви готові почати, дні тижня та час коли ви зможете волонтерити;
             |
             |Почнімо. Вкажіть своє імʼя:
             |/name - імʼя прізвище;
             |ПРИКЛАД: /name Тарас Бульба
             |
      """.stripMargin,
        unknownError -> "Щось пішло не за планом, будь ласка повторіть вашу попередню команду використовючи цей формат:",
        unsupportedMessageError -> "Ой, Я вас не зрозумів, використайте /help щоб отримати інформацію про те що я вмію",
        dateError -> "Формат дати не правельний, будь ласка спробуйте ще раз використовуючи цей формат ріц-місяць-день - 2022-05-01, приклад: /date 2022-06-02",
        dayError -> "Формат дня не првельний, будь ласка спробуйте ще раз використовуючи цей формат, приклад: /days mon tue wed thu fri sat sun",
        name ->
          """Дякую я отримав інормацію про ваше імʼя;
            |
            |Будь ласка напишіть ваш номер телефону з кодом країни:
            |
            |/phone - номер телефону;
            |ПРИКЛАД: /phone +491234567890
            |
            |""".stripMargin,
        phone ->
          """Дякую я отримав ваш номер телефону.
            |
            |Будь ласка скажіть чи можете ви допомагати як охоронець.
            |
            |Напишіть /security, у вас буде запитано чи ви моглиб бути охоронцем
            |ПРИКЛАД: /security
            |
            |""".stripMargin,
        oneOfTwo -> "Виберіть один з варіантів",
        securityYes ->
          """Дякую. Ви підтвердили що можете допомагати як охоронець.
            |
            |Будь ласка напишіть чи у вас є медичний або фармацевтичний досвід. Нам потрібна ця інформація щоб знати чи можете ви допомагати наприклад з сортуванням ліків.
            |
            |Напишіть /medic, у вас буде запитано чи маєте ви медичний або фармацевтичний досвід;
            |ПРИКЛАД: /medic
            |
            |""".stripMargin,
        securityNo ->
          """Дякую. Ви підтвердили що не можете допомагати як охоронець.
            |
            |Будь ласка напишіть чи у вас є медичний досвід. Нам потрібна ця інформація щоб знати чи можете ви допомагати наприклад з сортуванням ліків.
            |
            |Напишіть /medic, у вас буде запитано чи маєте ви медичний або фармацевтичний досвід;
            |ПРИКЛАД: /medic
            |
            |""".stripMargin,
        medicYes ->
          """Дякую. Ви підтвердили що маєте медичний або фармацевтичний досвід.
            |
            |Будь ласка напишіть коли ви готові почати?
            |
            |/date - вкажіть дату, формат: YYYY-MM-DD;
            |ПРИКЛАД: /date 2022-03-05
            |
            |""".stripMargin,
        medicNo ->
          """Дякую. Ви підтвердили що ви не маєте медичного або фармацевтичного досвіду.
            |
            |Будь ласка напишіть коли ви готові почати?
            |
            |/date - вкажіть дату, формат: YYYY-MM-DD;
            |ПРИКЛАД: /date 2022-03-05
            |
            |""".stripMargin,
        date ->
          """Дякую, я отримав дату з якої ви готові допомагати.
            |
            |Тепер скадіть в які дні ви готові допомагати.
            |
            |Напишіть /days - я попрошу вас вказати в які дні ви готові допомагати, формат: mon, tue, wed, thu, fri, sut, sun.
            |ПРИКЛАД: /days mon tue wed thu fri sut sun
            |
            |""".stripMargin,
        days ->
          """Дякую, я отримав інформацію в які дні ви готові допомагати.
            |
            |Останній крок. Будь ласка вкажіть коли ви готові допомагати(до обіду, після чи цілий день);
            |
            |Напишіть /time - у вас буде запитано в котрий час ви готові допомагати;
            |ПРИКЛАД: /time
            |
            |""".stripMargin,
        timeBeforeLunch ->
          """Дякую, ми отримали інформацію що ви можете допомагати до обіду. Тепер ви можете перевірити ваші дані.
            |
            |Напишіть /check щоб перевірити внесенні дані;
            |ПРИКЛАД: /check
            |
            |""".stripMargin,
        timeAfterLunch ->
          """Дякую, ми отримали інформацію що ви можете допомагати після обіду. Тепер ви можете перевірити ваші дані.
            |
            |Напишіть /check щоб перевірити внесенні дані;
            |ПРИКЛАД: /check
            |
            |""".stripMargin,
        timeWholeDay ->
          """Дякую, ми отримали інформацію що ви можете допомагати цілий день. Тепер ви можете перевірити ваші дані.
            |
            |Напишіть /check щоб перевірити внесенні дані;
            |ПРИКЛАД: /check
            |
            |""".stripMargin,
        checkNoData ->
          s"""Ми поки що не маємо даних про вас :(
             |
             |""".stripMargin,
        yes -> "так",
        no -> "ні",
        blBtn -> "до обіду",
        alBtn -> "після обіду",
        wdBtn -> "цілий день",
        mBtn -> "Я маю медичний або фармацивтичний досвід",
        nmBtn -> "Я не маю медичного або фармацивтичного досвіду"
      ),
      en -> Map(
        help -> s"""Please type /start to get more info and start you registration for voluntary, in case you already registered you can check your date with /check command.""".stripMargin,
        language ->
          s"""Hello, this telegram bot will help you to register for volunteering actives in Berlin which are aimed to support people of Ukraine due to russian invasion.
             |You will be asked to step by step fill in the registration form. This data will be forwarded to volunteers from Berlin who will contact you afterwards with the information about when and where help is needed.
             |
             |Name - name and surname;
             |Phone number - needed to reach you in a short notice;
             |Do you have medical or pharmaceutical background? - some volunteering activities e.g. sorting of medicines can be performed only with background
             |Start date - date when you are available to start;
             |
             |Let's start. Please specify commands as described below:
             |/name - name surname;
             |USAGE EXAMPLE: /name Taras Bulba
             |
      """.stripMargin,
        unknownError -> "Ups something went wrong, please repeat previous command with expected format:",
        unsupportedMessageError -> "Oops, I don't know what you mean, use /help to see what I can do.",
        dateError -> "Ups date format you have used are wrong, please try again with this format year-month-day - 2022-05-01, eg: /date 2022-06-02",
        dayError -> "Ups day format you have used are wrong, please try to type with this format, eg: /days mon tue wed thu fri sat sun",
        name ->
          """Thank you, I received your name;
            |
            |Please specify your phone number with country code:
            |
            |/phone - phone number;
            |USAGE EXAMPLE: /phone +491234567890
            |
            |""".stripMargin,
        phone ->
          """Thank you, I received your phone number.
            |
            |Please specify if you are ready to volunteer as a security guy.
            |
            |Type /security, you will be asked about your preference to volunteer as a security guy;
            |USAGE EXAMPLE: /security
            |
            |""".stripMargin,
        oneOfTwo -> "Please choose one of the options",
        securityYes ->
          """Thank you. You confirmed that you can volunteer as a security guy.
            |
            |Please specify if you have any medical or pharmaceutical background. This information is needed because some tasks like sorting medicines can be performed only with such background.
            |
            |Type /medic, you will be asked about medical or pharmaceutical background;
            |USAGE EXAMPLE: /medic
            |
            |""".stripMargin,
        securityNo ->
          """Thank you. You confirmed that you can't volunteer as security guy.
            |
            |Please specify if you have any medical or pharmaceutical background. This information is needed because some tasks like sorting medicines can be performed only with such background.
            |
            |Type /medic, you will be asked about medical or pharmaceutical background;
            |USAGE EXAMPLE: /medic
            |
            |""".stripMargin,
        medicYes ->
          """Thank you. You confirmed that you have medical or pharmaceutical background.
            |
            |Please specify when are you available to start?
            |
            |/date - specify the date, format: YYYY-MM-DD;
            |USAGE EXAMPLE: /date 2022-03-05
            |
            |""".stripMargin,
        medicNo ->
          """Thank you. You confirmed that you do not have medical or pharmaceutical background.
            |
            |Please specify when are you available to start?
            |
            |/date - specify the date, format: YYYY-MM-DD;
            |USAGE EXAMPLE: /date 2022-03-05
            |
            |""".stripMargin,
        date ->
          """Thank you, we received start date when you can start.
            |
            |Now please tell us which days you are available
            |
            |Type /days - you will be asked to specify which days you are ready to help, format: mon, tue, wed, thu, fri, sut, sun.
            |USAGE EXAMPLE: /days mon tue wed thu fri sut sun
            |
            |""".stripMargin,
        days ->
          """Thank you, I received days when you are ready to help.
            |
            |Last step. Please specify which part of the day is preferred by you (before lunch/ after lunch/ all day);
            |
            |Type /time - you will be asked about preferred time;
            |USAGE EXAMPLE: /time
            |
            |""".stripMargin,
        timeBeforeLunch ->
          """Thank you we received that you are available before lunch. Now you can check your data.
            |
            |Type /check to review the data we receieved from you;
            |USAGE EXAMPLE: /check
            |
            |""".stripMargin,
        timeAfterLunch ->
          """Thank you we received that you are available after lunch. Now you can check your data.
            |
            |Type /check to review the data we receieved from you;
            |USAGE EXAMPLE: /check
            |
            |""".stripMargin,
        timeWholeDay ->
          """Thank you we received that you are available whole day. Now you can check your data.
            |
            |Type /check to review the data we receieved from you;
            |USAGE EXAMPLE: /check
            |
            |""".stripMargin,
        checkNoData ->
          s"""There is no data from you yet :(
             |
             |""".stripMargin,
        yes -> "yes",
        no -> "no",
        blBtn -> "before lunch",
        alBtn -> "after lunch",
        wdBtn -> "whole day",
        mBtn -> "I have medical background",
        nmBtn -> "I don't have medical background"
      )
    )

    def checkMessageMap(d: ParsedData): Map[String, Map[String, String]] = {
      Map(
        ua -> Map(check ->
          s"""Дякую за вашу реєстрацію, нижче усі дані які ми від вас отримали:
             |
             |Ваше ім'я: ${d.name.getOrElse("не вказано")};
             |Ваш номер телефону: ${d.phone.getOrElse("не вказано")};
             |Чи можете ви допомагати як охоронець: ${d.keeper.map(_.toString).getOrElse("не вказано")};
             |У вас є медичний досвід: ${d.medic.map(_.toString).getOrElse("не вказано")};
             |З якої дати ви готові почати: ${d.availabilityMessage.map(_.toLocalDate.toString).getOrElse("не вказано")};
             |В які дні ви готові допомагати: ${d.days.getOrElse("не вказано")}
             |В який час ви готові допомагати: ${d.time.getOrElse("не вказано")}
             |Ваш телеграм: ${d.telegram.getOrElse("не вказано")}
             |
             |Якщо ви знайгли помилку, не хвилюйтесь, ви можете скористатис ще раз одною з навединих нижче команд:
             |/name - вказати ім'я;
             |/phone - вказати номер телефону;
             |/security - вказати чи готові ви допомогти як охоронець;
             |/medic - вказаьт чи у вас є медичний досвід;
             |/date - вказати дату коли ви шотові почати;
             |/days - вказати в які дні ви готові допомагати;
             |/time - вказаи в який час ви готові допомагати;
             |/check - перевірити надслані дані;
             |
             |""".stripMargin),
        en -> Map(check ->
          s"""Thank you for submitting data, here is the data which will be forwarded to volunteers:
             |
             |Your name: ${d.name.getOrElse("unspecified")};
             |Your phone number: ${d.phone.getOrElse("unspecified")};
             |Can you volunteer as a security guy: ${d.keeper.map(_.toString).getOrElse("unspecified")};
             |Do you have medical or pharmaceutical background: ${d.medic.map(_.toString).getOrElse("unspecified")};
             |Your start date: ${d.availabilityMessage.map(_.toLocalDate.toString).getOrElse("unspecified")};
             |Which days you can volunteer: ${d.days.getOrElse("unspecified")}
             |When you are available: ${d.time.getOrElse("unspecified")}
             |Your telegram username: ${d.telegram.getOrElse("unspecified")}
             |
             |If you found a mistake, don't worry, you can reuse one of the commands below to fix your data:
             |/name - send your name;
             |/phone - send your phone number;
             |/security - send information, if you can volunteer as a security guy;
             |/medic - send info about your medical background;
             |/date - send you start date;
             |/days - send on which days after the start date you can volunteer;
             |/time - send which part of the day are preferable for you to volunteer;
             |/check - check data which was collected from you;
             |
             |""".stripMargin))

    }
  }

}

@Singleton
class BotMessagesService @Inject()(ws: WSClient, chatBot: ChatBot, configuration: Configuration)(implicit ex: ExecutionContext) {

  import BotMessagesService._

  private val logger = LoggerFactory.getLogger(getClass)

  val token = configuration.get[String]("bot.token")

  Try(stopBot) match {
    case Success(_) => logger.info("Bot stopped on startup")
    case _          =>
      logger.info("There were no bots, starting new")
      runBot
  }

  def runBot = {
    chatBot.run()
  }

  def stopBot = {
    chatBot.shutdown()
  }

  def getMe = {
    ws.url(s"https://api.telegram.org/bot${token}/getMe").get().map {
      implicit res =>
        Json.parse(res.body).asOpt[BotInfo]
    }
  }
}

@Singleton
class ChatBot @Inject()(
  messageDao: MessageDao,
  configuration: Configuration
) extends AkkaTelegramBot with Polling with InlineQueries[Future] with Commands[Future] with Callbacks[Future] {
  import BotMessagesService.Translation._
  import cats.effect.unsafe.implicits.global

  val token = configuration.get[String]("bot.token")

  val client = new AkkaHttpClient(token)
  val MEDIC_TAG = "MEDIC_TAG"
  val NOT_MEDIC_TAG = "NOT_MEDIC_TAG"
  val BEFORE = "BEFORE_LUNCH"
  val AFTER = "AFTER_LUNCH"
  val ALL_DAY = "ALL_DAY"

  val LANGUAGE_UA = "LANGUAGE_UA"
  val LANGUAGE_EN = "LANGUAGE_EN"

  val KEEPER = "KEEPER"
  val NO_KEEPER = "NO_KEEPER"
  val keeper = "yes"
  val notKeeper = "no"
  val medic = "I have medical background"
  val notMedic = "I don't have medical background"
  val before = "before lunch"
  val after = "after lunch"
  val allDay = "whole day"

  val unspecified = "unspecified"
  val listOfSupported = List("/start", "/name", "/phone", "/security", "/date", "/days",
    "/help", "/time", "/medic", "/check", "/language")

  def withEmptyQueryValidation(
    query: String, command: String,
    daysValidator: Option[Regex] = None,
    dateValidator: Option[Regex] = None
  )(body: IO[Message])(implicit message: Message): Future[Unit] = {
    val condition = message.text.flatMap(_.split(" ").headOption).exists(listOfSupported.contains)
    val dateValidationCondition = dateValidator.forall(_.pattern.matcher(query).matches())
    val daysValidationCondition = query.split(" ").forall(el => daysValidator.forall(_.pattern.matcher(el).matches()))
    if (query.isEmpty && condition) {
      reply(s"${getReply(message.from.flatMap(_.id), unknownError)} ${s"/$command data".bold}".stripMargin, parseMode = ParseMode.Markdown).void
    } else if (!dateValidationCondition) {
      reply(getReply(message.from.flatMap(_.id), dateError), parseMode = ParseMode.Markdown).void
    } else if (!daysValidationCondition) {
      reply(getReply(message.from.flatMap(_.id), dayError), parseMode = ParseMode.Markdown).void
    } else if (!condition) {
      Future.successful(()).void
    } else {
      body.unsafeToFuture().void
    }

  }

  def languageBtn: InlineKeyboardMarkup =
    InlineKeyboardMarkup.singleRow(List(InlineKeyboardButton.callbackData("Українська", LANGUAGE_UA), InlineKeyboardButton("English", LANGUAGE_EN)))

  def medicBtn(userId: Option[Long]): InlineKeyboardMarkup =
    InlineKeyboardMarkup.singleRow(List(InlineKeyboardButton.callbackData(getReply(userId, mBtn), MEDIC_TAG), InlineKeyboardButton(getReply(userId, nmBtn), NOT_MEDIC_TAG)))

  def keeperBtn(userId: Option[Long]): InlineKeyboardMarkup =
    InlineKeyboardMarkup.singleRow(List(InlineKeyboardButton.callbackData(getReply(userId, yes), KEEPER), InlineKeyboardButton(getReply(userId, no), NO_KEEPER)))

  def timeBtn(userId: Option[Long]): InlineKeyboardMarkup =
    InlineKeyboardMarkup.singleRow(List(InlineKeyboardButton.callbackData(getReply(userId, blBtn), BEFORE), InlineKeyboardButton(getReply(userId, alBtn), AFTER), InlineKeyboardButton(getReply(userId, wdBtn), ALL_DAY)))

  def updateResultObject(telegram: Option[String], id: Option[Long]) = {
    prepareResult(telegram, id).map(messageDao.update).getOrElse {
      throw DaoException("Can't save data object to db")
    }
  }

  def getReply(user: Option[Long], key: String) = {
    if (Try(cacheMultiLanguage.getIfPresent(user.getOrElse(0L))).getOrElse(false)) {
      txtMap.get(ua).flatMap(_.get(key)).getOrElse(unspecified)
    } else {
      txtMap.get(en).flatMap(_.get(key)).getOrElse(unspecified)
    }
  }

  def getReplayWithCheck(user: Option[Long], parsedData: ParsedData, key: String) = {
    if (Try(cacheMultiLanguage.getIfPresent(user.getOrElse(0L))).getOrElse(false)) {
      checkMessageMap(parsedData).get(ua).flatMap(_.get(key)).getOrElse(unspecified)
    } else {
      checkMessageMap(parsedData).get(en).flatMap(_.get(key)).getOrElse(unspecified)
    }

  }

  onCommand("help") { implicit msg =>
    reply(
      getReply(msg.from.map(_.id), help),
      parseMode = ParseMode.Markdown
    ).void
  }

  onCommand("start") { implicit msg =>
    reply(
      s"""Hello before we start please choose language I should speak to you, instructions below.
         |Привіт перед початком будь ласка виберіть мову якою я буду розмовляти з вами, інструкції нижче.
         |
         |EN -> For English, press here: /language
         |UA -> Для українької мови, натисніть сюди: /language
      """.stripMargin,
      parseMode = ParseMode.Markdown
    ).void
  }

  onCommand("language") {
    implicit msg =>
      withArgs { _ =>
        reply(getReply(msg.from.map(_.id), oneOfTwo), replyMarkup = languageBtn).void
      }
  }

  onCallbackWithTag(LANGUAGE_EN) { implicit cbq =>
    cbq.message.map { implicit msg =>
      msg.from.foreach(usr => cacheMultiLanguage.put(cbq.from.id, false))
      reply(getReply(cbq.from.id, language)).void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCallbackWithTag(LANGUAGE_UA) { implicit cbq =>
    cbq.message.map { implicit msg =>
      msg.from.foreach(usr => cacheMultiLanguage.put(cbq.from.id, true))
      reply(getReply(cbq.from.id, language)).void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCommand("name") { implicit msg =>
    withArgs { args =>
      val query = args.mkString(" ")
      withEmptyQueryValidation(query, "name") {
        IO.fromFuture(IO(reply(
          getReply(msg.from.map(_.id), name),
          parseMode = ParseMode.Markdown).map {
          res =>
            val userId = msg.from.map(_.id)
            userId.foreach(uId => cache.put(s"${uId}_name", NameMessage(query)))
            updateResultObject(msg.from.flatMap(_.username), userId)
            res
        }))
      }
    }
  }

  onCommand("phone") { implicit msg =>
    withArgs { args =>
      val query = args.mkString(" ")

      withEmptyQueryValidation(query, "phone") {
        IO.fromFuture(IO(reply(
          getReply(msg.from.map(_.id), phone),
          parseMode = ParseMode.Markdown))).map { res =>
          val userId = msg.from.map(_.id)
          userId.foreach(uId => cache.put(s"${uId}_phone", PhoneMessage(query)))
          updateResultObject(msg.from.flatMap(_.username), userId)
          res
        }
      }
    }
  }

  onCommand("security") { implicit msg =>
    withArgs { _ =>
      reply(getReply(msg.from.map(_.id), oneOfTwo), replyMarkup = keeperBtn(msg.from.map(_.id))).void
    }
  }

  onCallbackWithTag(KEEPER) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, securityYes)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_keeper", SecurityInfoMessage(true))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCallbackWithTag(NO_KEEPER) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, securityNo)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_keeper", SecurityInfoMessage(false))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCommand("medic") { implicit msg =>
    withArgs { _ =>
      reply(getReply(msg.from.map(_.id), oneOfTwo), replyMarkup = medicBtn(msg.from.map(_.id))).void
    }
  }

  onCallbackWithTag(MEDIC_TAG) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, medicYes).stripMargin).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_medic", DoctorInfoMessage(true))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCallbackWithTag(NOT_MEDIC_TAG) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, medicNo)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_medic", DoctorInfoMessage(false))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with medic info received")).void
    }
  }

  onCommand("date") { implicit msg =>
    val date = raw"(\d{4})-(\d{2})-(\d{2})".r
    withArgs { args =>
      val query = args.mkString(" ")
      withEmptyQueryValidation(query, "date", dateValidator = Some(date)) {
        IO.fromFuture(IO(reply(
          getReply(msg.from.map(_.id), BotMessagesService.Translation.date),
          parseMode = ParseMode.Markdown))).map { res =>
          val userId = msg.from.map(_.id)
          userId.foreach(uId => cache.put(s"${uId}_date", StarDateMessage(Try(DateTime.parse(query)).getOrElse(DateTime.now()))))
          updateResultObject(msg.from.flatMap(_.username), userId)
          res
        }
      }
    }
  }

  onCommand("days") { implicit msg =>
    val days = raw"(mon)|(tue)|(wed)|(thu)|(fri)|(sat)|(sun)".r
    withArgs { args =>
      val query = args.mkString(" ")

      withEmptyQueryValidation(query, "days", daysValidator = Some(days)) {
        IO.fromFuture(IO(reply(
          getReply(msg.from.map(_.id), BotMessagesService.Translation.days),
          parseMode = ParseMode.Markdown))).map { res =>
          val userId = msg.from.map(_.id)
          userId.foreach(uId => cache.put(s"${uId}_days", PreferableDays(query)))
          updateResultObject(msg.from.flatMap(_.username), userId)
          res
        }
      }
    }
  }

  onCommand("time") { implicit msg =>
    reply(getReply(msg.from.map(_.id), oneOfTwo), replyMarkup = timeBtn(msg.from.map(_.id))).void
  }

  onCallbackWithTag(BEFORE) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(getReply(cbq.from.id, timeBeforeLunch)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_time", TimeMessage(before))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with time info received")).void
    }
  }

  onCallbackWithTag(AFTER) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, timeAfterLunch)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_time", TimeMessage(after))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with time info received")).void
    }
  }

  onCallbackWithTag(ALL_DAY) { implicit cbq =>
    cbq.message.map { implicit msg =>
      reply(
        getReply(cbq.from.id, timeWholeDay)).map { res =>
        val userId = cbq.from.id
        cache.put(s"${userId}_time", TimeMessage(allDay))
        updateResultObject(cbq.from.username, userId)
        res
      }.void
    }.getOrElse {
      Future.successful(logger.error("Oops, something went wrong with time info received")).void
    }
  }

  onCommand("check") { implicit msg =>
    msg.from.map(_.id).map {
      id =>
        (messageDao.findById(id.toString).recover {
          case NonFatal(ex) =>
            logger.error(ex.getMessage)
            None
        }).flatMap {
          case Some(d) => reply(getReplayWithCheck(id, d, check), parseMode = ParseMode.Markdown).void
          case _       => reply(getReply(id, checkNoData), parseMode = ParseMode.Markdown).void
        }
    }.getOrElse(Future.successful(logger.warn("Can't find data in cache and DB, user not registered yet")))
  }

  onMessage {
    implicit msg =>
      msg.text.map {
        el =>
          if (!el.contains("/") || !listOfSupported.contains(el.split(" ").headOption.getOrElse("None"))) {
            reply(getReply(msg.from.map(_.id), unsupportedMessageError)).void
          } else Future.successful(()).void
      }.getOrElse(Future.successful(()).void)
  }

}