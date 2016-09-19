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

import java.time.LocalDateTime

import org.pircbotx.{Configuration, PircBotX}
import akka.actor.{Actor, ActorSystem, Props}
import org.pircbotx.PircBotX
import org.apache.logging.log4j.LogManager
import org.pircbotx.hooks.events._
import org.pircbotx.output.OutputChannel
import java.time.{ZonedDateTime, ZoneId, ZoneOffset, LocalDateTime, Instant}
import java.time.format.DateTimeFormatter
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

  case class Send(msg: String)

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
  case class WrapPartEvent(event: PartEvent)

  def start() = {
    Environment.initialize()
    val bot = ActorSystem("Pianobot").actorOf(Props(new Pianobot), "pianobot")
    Parser.setActor(bot)
    bot ! "start"
    bot
  }
}

class Pianobot extends Actor {
  private val users = new mutable.HashSet[String]()
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
  private val knowsSong = Array[String](
    "/me nods. \"Sure, part of every piano-fighter's repertoire.\"",
    "/me mm-hmms. \"Played it at Mountain Stage, even.\"",
    "/me looks offended.  \"Of course I know it!\"",
    "/me nods. \"Why, you want to hear it?\"",
    "/me answers by playing a few bars."
  )
  private val stumpedSong = Array[String](
    "/me looks frustrated, a sure sign of being stumped.",
    "/me gives a curt, \"No.\"",
    "/me avoids admitting ignorance by pretending he didn't hear the question.",
    "/me says, \"No, and not even if you hum a few bars.\"",
    "/me says, \"Not a standard part of the repertoire, no.\""
  )
  private var runPlayThread = true
  private val runPlayThreadSemaphore = new java.util.concurrent.Semaphore(1)
  new Thread(new Runnable() {
    override def run() = {
      while (getPlayThreadShouldRun) {
        engine.isConnected match {
          case false => Thread.sleep(500)
          case true =>
            dequeue match {
              case None => Thread.sleep(500)
              case Some((song, length)) =>
                val emote = startupEmotes(prng.nextInt(startupEmotes.length))
                var remaining = length
                self ! Send(s"""$emote"$song".""")
                while (remaining > 0) {
                  var delay = prng.nextInt(30) + 60
                  if (delay > remaining) delay = remaining
                  logger.info(s"waiting $delay seconds")
                  remaining -= delay
                  Thread.sleep(delay * 1000)
                  remaining match {
                    case 0 =>
                      val msg = endingEmotes(prng.nextInt(endingEmotes.length))
                      self ! Send (msg)
                    case _ =>
                      val msg = playingEmotes(prng.nextInt(playingEmotes.length))
                      self ! Send (msg + s"$song")
                  }
              }
          }
        }
      }
    }
  }).start()

  def getPlayThreadShouldRun : Boolean = {
    runPlayThreadSemaphore.acquireUninterruptibly()
    val rv = runPlayThread
    runPlayThreadSemaphore.release()
    rv
  }

  def setPlayThreadShouldRun(value : Boolean) = {
    runPlayThreadSemaphore.acquireUninterruptibly()
    runPlayThread = value
    runPlayThreadSemaphore.release()
  }

  def enqueue(song: String, length: Int) : Unit = {
    queueSemaphore.acquireUninterruptibly()
    if (playlist.size < 10)
      playlist += ((song, length))
    queueSemaphore.release()
  }

  def dequeue : Option[(String, Int)] = {
    queueSemaphore.acquireUninterruptibly()
    val rv = playlist.isEmpty match {
      case false =>
        Some(playlist.dequeue())
      case true => None
    }
    queueSemaphore.release()
    rv
  }

  def getQueueSize : Int = {
    queueSemaphore.acquireUninterruptibly()
    val rv = playlist.size
    queueSemaphore.release()
    rv
  }

  def checkMessagesFor(nick: String) = {
    val messages = SQLUtilities.getMessagesFor(nick)
    logger.info(s"checked for $nick and found ${messages.length} messages")
    messages.isEmpty match {
      case true => ;
      case false =>
        engine.sendIRC.message(nick, "So, people left some messages for you.  All times are UTC.")
        for ((source, communique, seconds) <- messages) {
          val ltime = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault())
          val stamp = ZonedDateTime.ofLocal(ltime, ZoneId.systemDefault(), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
          engine.sendIRC.message(nick, s"$stamp | $source | $communique")
        }
        SQLUtilities.flushMessagesFor(nick)
    }
  }

  def receive = {

    case "start" =>
      if (!engine.isConnected) {
        logger.info("Pianobot spinning up")
        thread.start()
      }

    case "stop" =>
      shuttingDown = true
      setPlayThreadShouldRun(false)
      if (engine.isConnected)
        engine.sendIRC().quitServer("went home -- normal disconnect")
      logger.info("Pianobot shutting down")
      System.exit(0)

    case Send(msg) => engine.sendIRC().message(channel, msg)

    case HaveYouSeen(nick) =>
      users.contains(nick) match {
        case true => self ! Send("/me points in " + nick + "'s direction.")
        case false => SQLUtilities.lastSaw(nick) match {
          case None => self ! Send("/me shakes his head no.")
          case Some(timestamp) =>
            val ltime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
            val stamp = ZonedDateTime.ofLocal(ltime, ZoneId.systemDefault(), ZoneOffset.UTC)
              .format(DateTimeFormatter.RFC_1123_DATE_TIME)
            self ! Send("/me recites with RFC1123-like precision, \"" + stamp + ", boss.\"")
        }
      }

    case GoHome(speaker) =>
      self ! Send("/me dutifully gathers up his music and goes home.")
      self ! "stop"

    case DoYouKnow(speaker, songname, artist) =>
      val knows = knowsSong(prng.nextInt(knowsSong.length))
      val stumped = stumpedSong(prng.nextInt(stumpedSong.length))
      val msg = artist match {
        case Some(x) =>
          SQLUtilities.isSongKnown(x, songname) match {
            case true => knows
            case false => stumped
          }
        case None =>
          val all_artists = SQLUtilities.getAllCovers(songname)
          all_artists.isEmpty match {
            case true => stumped
            case false => knows
          }
      }
      self ! Send(msg)

    case EnqueueSong(speaker, songname, artist) =>
      logger.info(s"asked to queue $songname")
      getQueueSize < 10 match {
        case false =>
          logger.info("rejected song request -- queue full")
          self ! Send("/me shakes his head no. \"Have a full set already.\"")
        case true =>
          (artist match {
            case Some(x) => Some(x)
            case None =>
              val covers = SQLUtilities.getAllCovers(songname)
              covers.isEmpty match {
                case true => None
                case false => Some(scala.util.Random.shuffle(covers.toList).head)
              }
          }) match {
            case None =>
              logger.info("rejected song request -- song unknown")
              self ! Send("/me shakes his head no.  \"Don't know it.\"")
            case Some (band) => SQLUtilities.isSongKnown(band, songname) match {
              case false =>
                logger.info("rejected song request -- song unknown")
                self ! Send("/me shakes his head no.  \"Don't know it.\"")
              case true =>
                val length = SQLUtilities.getSongLength(band, songname)
                length > 0 match {
                  case true =>
                    logger.info(s"enqueing $songname by $band")
                    self ! Send("/me hurriedly adds it to his set list.")
                    enqueue(songname, length)
                  case false =>
                    logger.info("rejected song request -- bad length")
                }
            }
          }
      }

    case LeaveMessageFor(speaker, target, msg) =>
      users.contains(target) match {
        case true =>
          speaker == target match {
            case true =>
              val msg1 = "/me says slowly, \"You want to leave a message for ... yourself.\""
              val msg2 = "/me calls over to Tom, \"No more booze for " + speaker + ".\""
              self ! Send(msg1)
              self ! Send(msg2)
            case false =>
              val msg1 = "/me hollers, \"Hey, " + target + "! " + speaker + "'s looking for you!\""
              self ! Send(msg1)
          }
        case false =>
          SQLUtilities.isNickKnown(target) match {
            case false => self ! Send("/me shakes his head no. \"I don't know that person.\"")
            case true =>
              SQLUtilities.leaveMessageFor(speaker, target, msg)
              self ! Send("/me writes it down. \"Yeah, yeah, I'll pass it on, maybe.\"")
          }
      }

    case WrapConnectEvent(event) =>
      logger.info(s"successfully connected to ${Environment.options("irc server")}")
      setPlayThreadShouldRun(true)

    case WrapConnectAttemptFailedEvent(event) =>
      logger.info(s"failed to connect to ${Environment.options("irc server")} -- aborting")
      System.exit(1)

    case WrapDisconnectEvent(event) =>
      for (nick <- users) SQLUtilities.lastSaw(nick)
      logger.info("server disconnected")
      if (!shuttingDown) {
        self ! "stop"
      }

    case WrapExceptionEvent(event) =>
      val e = event.getException
      logger.fatal(s"received exception ${e.toString}/${e.getMessage}")
      System.exit(1)

    case WrapNickAlreadyInUseEvent(event) =>
      logger.fatal("error -- nick already in use -- aborting")
      System.exit(1)

    case WrapJoinEvent(event) =>
      logger.info("received join event")
      event.getUser.getNick == name match {
        case true => ;
        case false =>
          val nick = event.getUser.getNick
          val capset = SQLUtilities.getCapabilitiesFor(nick)
          capset.contains("admin") || capset.contains("friend") || event.getUser.isIrcop match {
            case true => self ! Send(s"/me gives a nod of greeting to $nick.")
            case false => ;
          }
          SQLUtilities.sawPerson(nick)
          users.contains(nick) match {
            case true => ;
            case false => users += nick
          }
          checkMessagesFor(nick)
      }

    case WrapKickEvent(event) =>
      logger.info("received kick event -- shutting down")
      self ! "stop"

    case WrapMessageEvent(event) =>
      logger.info("received message event")
      try {
        Parser(event.getUser.getNick, event.getMessage)
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
        SQLUtilities.sawPeople(
          for (i <- event.getUsers.iterator.asScala.toIterable) yield i.getNick)
        for (i <- event.getUsers.iterator().asScala.toIterable) {
          checkMessagesFor(i.getNick)
          users += i.getNick
        }
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case WrapPartEvent(event) =>
      logger.info("received part event")
      val nick = event.getUser.getNick
      SQLUtilities.sawPerson(nick)
      if (users.contains(nick)) users -= nick

    case _ =>
      logger.info("received unknown message")
  }
}
