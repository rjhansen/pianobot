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
import org.pircbotx.output.OutputChannel

import scala.collection.JavaConverters._
import scala.collection.mutable
import engineering.hansen.pianobot.Pianobot._

object Pianobot {
  abstract class Response
  val csprng = new java.security.SecureRandom()
  val prng = new scala.util.Random(csprng.nextLong)

  case class DoYouKnow(speaker: String, name: String, artist: Option[String]) extends Response
  case class EnqueueSong(speaker: String, name: String, artist: Option[String]) extends Response
  case class LeaveMessageFor(speaker: String, target: String, msg: String) extends Response
  case class GoHome(speaker: String) extends Response
  case class HaveYouSeen(who: String) extends Response

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

  def start() = {
    val performance = ActorSystem("Pianobot")
    val bot = performance.actorOf(Props(new Pianobot), "pianobot")
    bot ! "start"
    bot
  }
}

class Pianobot extends Actor {
  private val playlist = new mutable.Queue[(String, Int)]()
  private val queueSemaphore = new java.util.concurrent.Semaphore(1)
  private val listener = new PianobotListener()
  listener.setActor(self)
  private val logger = LogManager.getLogger(getClass)
  private val server = Environment.options("irc server")
  private val name = Environment.options("bot")
  private val password = Environment.options("password")
  private val channel = Environment.options("irc channel")
  private val config = new Configuration.Builder()
    .setName(name)
    .setNickservPassword(password)
    .addServer(server)
    .addAutoJoinChannel(channel)
    .addListener(listener)
    .setAutoReconnect(false)
    .buildConfiguration()
  private val engine = new PircBotX(config)
  private var shuttingDown = false
  private var output : OutputChannel = _
  private val thread: Thread = new Thread(new Runnable {
    override def run() = {
      engine.startBot()
    }
  })
  private val startupEmotes = Array[String](
    "/me cracks his knuckles and begins playing ",
    "/me rummages through his stash of sheet music for ",
    "/me takes a pull off his beer and starts up with ",
    "/me mumbles to himself, \"Negative, Ghostrider, the pattern is full,\" before pounding out ",
    "/me realizes he's paid to play, and so begins doing his own take on "
    )
  private val endingEmotes = Array[String](
    "/me finishes the piece with a flourish worthy of Warren Zevon.",
    "/me reaches the end of the song.",
    "/me pounds the finish like Bob Seger.",
    "/me finishes the piece, then checks his set list to see what's next.",
    "/me brings it to a finish worthy of a Julliard audition."
  )
  private val playingEmotes = Array[String](
    "/me works his way through ",
    "/me improvises a riff on ",
    "/me takes it to a bridge nobody knew was in ",
    "/me justifies his place among the minor gods of dive-bar pianists with ",
    "/me plays improvised arpeggios through  "
  )
  private var runPlayThread = true
  private val runPlayThreadSemaphore = new java.util.concurrent.Semaphore(1)
  private val playThread = new Thread(new Runnable() {
    override def run() = {
      while (getPlayThreadShouldRun) {
        if (! engine.isConnected)
          Thread.sleep(1000)
        else {
          dequeue match {
            case None => Thread.sleep(1000)
            case Some((song, length)) =>
              val emote = startupEmotes(prng.nextInt(startupEmotes.length))
              var remaining = length
              engine.sendIRC().action(channel, s"""${emote} "${song}".""")
              while (remaining > 0) {
                var delay = prng.nextInt(30) + 60
                if (delay > remaining) delay = remaining
                remaining -= delay
                Thread.sleep(delay * 1000)
                val msg = (remaining match {
                  case 0 => endingEmotes(prng.nextInt(endingEmotes.length))
                  case _ => playingEmotes(prng.nextInt(playingEmotes.length))
                })
                engine.sendIRC.action(channel, msg + s""""${song}"""")
              }
          }
        }
      }
    }
  })
  playThread.start()

  def getPlayThreadShouldRun : Boolean = {
    runPlayThreadSemaphore.acquireUninterruptibly()
    val rv = runPlayThread
    runPlayThreadSemaphore.release
    rv
  }

  def setPlayThreadShouldRun(value : Boolean) = {
    runPlayThreadSemaphore.acquireUninterruptibly()
    runPlayThread = value
    runPlayThreadSemaphore.release
  }

  def enqueue(song: String, length: Int) : Unit = {
    queueSemaphore.acquireUninterruptibly()
    playlist += ((song, length))
    queueSemaphore.release()
  }

  def dequeue() : Option[(String, Int)] = {
    queueSemaphore.acquireUninterruptibly()
    val rv = playlist.isEmpty match {
      case false =>
        Some(playlist.dequeue())
      case true => None
    }
    queueSemaphore.release()
    rv
  }



  def receive = {

    case "start" =>
      setPlayThreadShouldRun(true)
      if (!engine.isConnected) {
        logger.info("Pianobot spinning up")
        thread.start()
      }

    case "stop" =>
      shuttingDown = true
      setPlayThreadShouldRun(false)
      if (engine.isConnected)
        engine.close()
      logger.info("Pianobot shutting down")
      thread.join(100)
      context.system.terminate()

    case DoYouKnow(speaker, name, artist) =>
      val knows = "/me nods. \"Sure. Part of every ivory-tickler's repertoire.\""
      val stumped = "/me scowls, a sure sign he's been stumped."
      val msg = artist match {
        case Some(x) =>
          SQLUtilities.IsSongKnown(x, name) match {
            case true => knows
            case false => stumped
          }
        case None =>
          val all_artists = SQLUtilities.GetAllCovers(name)
          all_artists.isEmpty match {
            case true => stumped
            case false => knows
          }
      }
      engine.sendIRC().action(speaker, msg)

    case EnqueueSong(speaker, name, artist) =>
      val asDoneBy = artist match {
        case Some(x) => x
        case None =>
          val covers = SQLUtilities.GetAllCovers(name)
          covers.isEmpty match {
            case true => "this is not a match"
            case false => scala.util.Random.shuffle(covers.toList).head
          }
      }
      asDoneBy match {
        case "this is not a match" => ;
        case _ =>
          val length = SQLUtilities.GetSongLength(asDoneBy, name)
          if (length > 0) {
            playlist += ((name, length))
          }
      }

    case WrapConnectEvent(event) =>
      logger.info(s"successfully connected to ${Environment.options("irc server")}")

    case WrapConnectAttemptFailedEvent(event) =>
      logger.info(s"failed to connect to ${Environment.options("irc server")} -- aborting")
      System.exit(1)

    case WrapDisconnectEvent(event) =>
      logger.info("server disconnected")
      if (!shuttingDown) {
        self ! "stop"
      }

    case WrapExceptionEvent(event) =>
      val e = event.getException()
      logger.fatal(s"received exception ${e.toString}/${e.getMessage}")
      System.exit(1)

    case WrapNickAlreadyInUseEvent(event) =>
      logger.fatal("error -- nick already in use -- aborting")
      System.exit(1)

    case WrapJoinEvent(event) =>
      logger.info("received join event")
      try {
        val user = event.getUser
        val nick = user.getNick
        val capset = SQLUtilities.GetCapabilitiesFor(nick)
        if (capset.contains("admin") || capset.contains("friend") || user.isIrcop) {
          val resp = s"/me gives a nod of greeting to ${nick}."
          engine.send().action(Environment.options("irc channel"), resp)
        }
        SQLUtilities.SawPerson(nick)
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapKickEvent(event) =>
      logger.info("received kick event -- shutting down")
      self ! "stop"

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
      self ! "stop"

    case WrapUserListEvent(event) =>
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
