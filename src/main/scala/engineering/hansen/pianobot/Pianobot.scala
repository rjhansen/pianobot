package engineering.hansen.pianobot

/*
 * Copyright (c) 2016, Rob Hansen &lt;rob@hansen.engineering&gt;.
 *
 * Permission to use, copy, modify, and/or distribute this software
 * for any purpose with or without fee is hereby granted, provided
 * that the above copyright notice and this permission notice
 * appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
 * WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

import org.pircbotx.{Configuration, PircBotX}
import akka.actor.{Actor, ActorSystem, Props}
import org.pircbotx.PircBotX
import org.apache.logging.log4j.LogManager
import org.pircbotx.hooks.events._

import scala.collection.JavaConverters._

object Pianobot {
  case class WrapConnectEvent(event: ConnectEvent)
  case class WrapConnectAttemptFailedEvent(event: ConnectAttemptFailedEvent)
  case class WrapDisconnectEvent(event: DisconnectEvent)
  case class WrapExceptionEvent(event: ExceptionEvent)
  case class WrapNickAlreadyInUseEvent(event: NickAlreadyInUseEvent)
  case class WrapJoinEvent(event: JoinEvent)
  case class WrapKickEvent(event: KickEvent)
  case class WrapMessageEvent(event: MessageEvent)
  case class WrapPrivateMessageEvent(event: PrivateMessageEvent)
  case class WrapQuitEvent(event: QuitEvent)
  case class WrapUserListEvent(event: UserListEvent)
  case class EnqueueSong(name: String, length: Int)

  def start() = {
    val performance = ActorSystem("Pianobot")
    val listener = new PianobotListener
    val bot = performance.actorOf(Props(new Pianobot(listener)), "pianobot")
    listener.setActor(bot)
    bot ! "start"
    bot
  }
}

class Pianobot(listener: PianobotListener) extends Actor {
  private val logger = LogManager.getLogger(getClass)
  // FIXME: this should read in Environment.options for server/chan information.
  private val config = new Configuration.Builder()
    .setName(Environment.options("bot"))
    .addServer("irc.freenode.net")
    .addAutoJoinChannel("#pircbotx")
    .addListener(listener)
    .buildConfiguration()
  private val engine = new PircBotX(config)
  private val thread: Thread = new Thread(new Runnable {
    override def run() = {
      engine.startBot()
    }
  })

  def receive = {

    case "start" =>
      if (!engine.isConnected) {
        logger.info("Pianobot spinning up")
        thread.start()
      }

    case "stop" =>
      if (engine.isConnected)
        engine.close()
      logger.info("Pianobot shutting down")
      thread.join(100)
      context.system.terminate()

    case Pianobot.WrapConnectEvent(event) =>
      logger.info("received connect event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapConnectAttemptFailedEvent(event) =>
      logger.info("received connect attempt failed event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapDisconnectEvent(event) =>
      logger.info("received disconnect event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapExceptionEvent(event) =>
      logger.fatal("received exception event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapNickAlreadyInUseEvent(event) =>
      logger.info("received nick already in use event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapJoinEvent(event) =>
      logger.info("received join event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapKickEvent(event) =>
      logger.info("received kick event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapMessageEvent(event) =>
      logger.info("received message event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapPrivateMessageEvent(event) =>
      logger.info("received private message event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapQuitEvent(event) =>
      logger.info("received quit event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapUserListEvent(event) =>
      logger.info("received user list event")
      try {
        SQLUtilities.SawPeople(
          for (i <- event.getUsers.iterator.asScala.toIterable) yield i.getNick)
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case _ =>
      logger.info("received unknown message")
  }
}
