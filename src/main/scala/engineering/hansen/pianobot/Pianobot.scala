package engineering.hansen.pianobot

/**
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

import scala.collection.JavaConverters._

object Pianobot {
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
  val logger = LogManager.getLogger(getClass)
  // FIXME: this should read in Environment.options for server/chan information.
  val config = new Configuration.Builder()
    .setName(Environment.options("bot"))
    .addServer("irc.freenode.net")
    .addAutoJoinChannel("#pircbotx")
    .addListener(listener)
    .buildConfiguration()
  val engine = new PircBotX(config)
  var isRunning = false
  val semaphore = new java.util.concurrent.Semaphore(1)
  var thread: Thread = _

  def setIsRunning(state: Boolean) = {
    semaphore.acquireUninterruptibly()
    isRunning = state
    semaphore.release()
  }

  def getIsRunning : Boolean = {
    var rv = false
    semaphore.acquireUninterruptibly()
    rv = isRunning
    semaphore.release()
    rv
  }

  def receive = {

    case "start" =>
      if (false == getIsRunning) {
        logger.info("Pianobot spinning up")
        thread = new Thread(new Runnable {
          override def run() = {
            engine.startBot()
          }
        })
        thread.start()
      }

    case "stop" =>
      if (getIsRunning)
        engine.close()
      logger.info("Pianobot shutting down")
      thread.join(100)
      context.system.terminate()

    case WrapConnectEvent(event) =>
      logger.info("received connect event")
      setIsRunning(true)
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapConnectAttemptFailedEvent(event) =>
      logger.info("received connect attempt failed event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapDisconnectEvent(event) =>
      logger.info("received disconnect event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapExceptionEvent(event) =>
      logger.fatal("received exception event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapNickAlreadyInUseEvent(event) =>
      logger.info("received nick already in use event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapJoinEvent(event) =>
      logger.info("received join event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapKickEvent(event) =>
      logger.info("received kick event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapMessageEvent(event) =>
      logger.info("received message event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapPrivateMessageEvent(event) =>
      logger.info("received private message event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapQuitEvent(event) =>
      logger.info("received quit event")
      try {

      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapUserListEvent(event) =>
      logger.info("received user list event")
      try {
        SQLUtilities.sawPeople(
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
