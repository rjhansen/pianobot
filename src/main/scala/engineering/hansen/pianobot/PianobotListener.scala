package engineering.hansen.pianobot

import org.pircbotx.{Configuration, PircBotX}
import org.pircbotx.hooks.{ListenerAdapter}
import org.pircbotx.hooks.events._
import scala.collection.JavaConverters._
import org.apache.logging.log4j.LogManager

class PianobotListener extends ListenerAdapter {
  private val logger = LogManager.getLogger(getClass())
  private val server = Environment.options("irc server")

  override def onConnect(e: ConnectEvent) = {
    logger.info(s"""connected to $server""")
  }

  override def onConnectAttemptFailed(e: ConnectAttemptFailedEvent) = {
    logger.fatal(s"""could not connect to $server""")
    System.exit(0)
  }

  override def onDisconnect(e: DisconnectEvent) = {
    logger.info(s"""disconnected from $server""")
    logger.info("now terminating... hope you enjoyed Pianobot")
    System.exit(0)
  }

  override def onException(e: ExceptionEvent) = {
    logger.fatal(s"""exception: ${e.getException().toString}""")
    logger.fatal(s"""exception message: ${e.getMessage()}""")
    System.exit(1)
  }

  override def onNickAlreadyInUse(e: NickAlreadyInUseEvent) = {
    logger.fatal(s"nick already in use.  Aborting.")
    System.exit(1)
  }

  override def onJoin(e: JoinEvent) = {
    // FIXME.  Lots of logic to go here!
    logger.info(s"${e.getUserHostmask().getNick()} joined room")
  }

  override def onKick(e: KickEvent) = {
    logger.fatal(s"we just got kicked by ${e.getUserHostmask().getNick()}")
    System.exit(1)
  }

  override def onMessage(e: MessageEvent) = {
    logger.debug("received message")
    // FIXME.  Lots of logic to go here!
  }

  override def onPrivateMessage(e: PrivateMessageEvent) = {
    logger.debug("received private message")
    // FIXME.  Lots of logic to go here!
  }

  override def onQuit(e: QuitEvent) = {
    // FIXME.  Lots of logic to go here!
    logger.info(s"${e.getUserHostmask().getNick()} quit (${e.getReason()})")
  }

  override def onUserList(e: UserListEvent) = {
    val rx = "^([^!]+)!.*$".r
    SQLUtilities.sawPeople(
      for (i: String <-
        e.getUsers.iterator.asScala.toList.flatMap((u: org.pircbotx.User) => u.getNick() match {
          case rx(name) => Some(name)
          case _ => None
        })) yield i)
  }
}
