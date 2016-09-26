package engineering.hansen.pianobot

/*
 * Copyright (c) 2016, Rob Hansen <rob@hansen.engineering>.
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


import akka.actor.{Actor, ActorSystem, Props}
import org.pircbotx.Configuration
import org.pircbotx.PircBotX
import org.pircbotx.hooks.events._
import org.apache.logging.log4j.LogManager
import java.time._
import java.time.format.DateTimeFormatter

import scala.collection.JavaConverters._
import scala.collection.mutable
import engineering.hansen.pianobot.Utilities._

object Pianobot {
  abstract class Response
  case class DoYouKnow(speaker: String, name: String, artist: Option[String]) extends Response
  case class KnownBy(speaker: String, bandname: String) extends Response
  case class EnqueueSong(speaker: String, name: String, artist: Option[String]) extends Response
  case class LeaveMessageFor(speaker: String, target: String, msg: String) extends Response
  case class GoHome(speaker: String) extends Response
  case class HaveYouSeen(who: String) extends Response

  case class Send(msg: String)
  case class Emote(msg: String)

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

  val actorSystem = ActorSystem("Pianobot")
  val bot = actorSystem.actorOf(Props(new Pianobot), "pianobot")
  val server = Environment.options("irc server")
  val name = Environment.options("bot")
  val password = Environment.options("password")
  val channel = Environment.options("irc channel")


  def start() = {
    Environment.initialize()
    val bot = ActorSystem("Pianobot").actorOf(Props(new Pianobot), "pianobot")
    Parser.setActor(bot)
    bot ! "start"
    bot
  }
}


class Pianobot extends Actor {
  private val connection = SQLUtilities.getConnection
  private val users = new mutable.HashSet[String]()
  private val playlist = new mutable.Queue[(String, Int)]()
  private val queueSemaphore = new java.util.concurrent.Semaphore(1)
  private val listener = new PianobotListener()
  listener.setActor(self)
  private val logger = LogManager.getLogger(getClass)
  private val config = new Configuration.Builder()
    .setName(Pianobot.name)
    .setNickservPassword(Pianobot.password)
    .addServer(Pianobot.server)
    .addAutoJoinChannel(Pianobot.channel)
    .addListener(listener)
    .setAutoReconnect(false)
    .buildConfiguration()
  private val engine = new PircBotX(config)
  private var shuttingDown = false
  private val thread: Thread = new Thread(new Runnable {
    override def run() = {
      engine.startBot()
    }
  })
  private val startupEmotes = Array[String](
    "cracks his knuckles and begins playing ",
    "rummages through his stash of sheet music for ",
    "takes a pull off his beer and starts up with ",
    "mumbles to himself, \"Negative, Ghostrider, the pattern is full,\" before pounding out ",
    "realizes he's paid to play, and so begins doing his own take on "
    )
  private val endingEmotes = Array[String](
    "finishes the piece with a flourish worthy of Warren Zevon.",
    "reaches the end of the song.",
    "pounds the finish like Bob Seger.",
    "finishes the piece, then checks his set list to see what's next.",
    "brings it to a finish worthy of a Julliard audition."
  )
  private val playingEmotes = Array[String](
    "works his way through ",
    "improvises a riff on ",
    "takes it to a bridge nobody knew was in ",
    "justifies his place among the minor gods of dive-bar pianists with ",
    "plays improvised arpeggios through  "
  )
  private val knowsSong = Array[String](
    "nods. \"Sure, part of every piano-fighter's repertoire.\"",
    "mm-hmms. \"Played it at Mountain Stage, even.\"",
    "looks offended.  \"Of course I know it!\"",
    "nods. \"Why, you want to hear it?\"",
    "answers by playing a few bars."
  )
  private val stumpedSong = Array[String](
    "looks frustrated, a sure sign of being stumped.",
    "gives a curt, \"No.\"",
    "avoids admitting ignorance by pretending he didn't hear the question.",
    "says, \"No, and not even if you hum a few bars.\"",
    "says, \"Not a standard part of the repertoire, no.\""
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
                self ! Pianobot.Emote(s"""$emote"$song".""")
                while (remaining > 0) {
                  var delay = prng.nextInt(30) + 60
                  if (delay > remaining) delay = remaining
                  logger.info(s"waiting $delay seconds")
                  remaining -= delay
                  Thread.sleep(delay * 1000)
                  remaining match {
                    case 0 =>
                      val msg = endingEmotes(prng.nextInt(endingEmotes.length))
                      self ! Pianobot.Emote(msg)
                    case _ =>
                      val msg = playingEmotes(prng.nextInt(playingEmotes.length))
                      self ! Pianobot.Emote(msg + s"$song")
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
    val m = SQLUtilities.getMessagesFor(connection, nick)
    m match {
      case None => ;
      case Some(messages) =>
        engine.sendIRC.message(nick, "So, people left some messages for you.  All times are UTC.")
        for ((source, communique, seconds) <- messages) {
          val ltime = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault())
          val stamp = ZonedDateTime.ofLocal(ltime, ZoneId.systemDefault(), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
          engine.sendIRC.message(nick, s"$stamp | $source | $communique")
        }
        SQLUtilities.flushMessagesFor(connection, nick)
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

    case Pianobot.Send(msg) => engine.sendIRC().message(Pianobot.channel, msg)
    case Pianobot.Emote(msg) => engine.sendIRC().action(Pianobot.channel, msg)

    case Pianobot.HaveYouSeen(nick) =>
      users.contains(nick) match {
        case true => self ! Pianobot.Emote("points in " + nick + "'s direction.")
        case false =>
          SQLUtilities.getLastSeen(connection, nick) match {
            case None => self ! Pianobot.Emote("shrugs.  \"" + nick + "?  Whozzat?\"")
            case Some(stamp) => self ! Pianobot.Send(s"I last saw $nick at $stamp.")
          }
      }

    case Pianobot.GoHome(speaker) =>
      self ! Pianobot.Emote("dutifully gathers up his music and goes home.")
      self ! "stop"

    case Pianobot.DoYouKnow(speaker, songname, artist) =>
      logger.info(s"checking if $songname is in the repertoire")
      val knows = knowsSong(prng.nextInt(knowsSong.length))
      val stumped = stumpedSong(prng.nextInt(stumpedSong.length))
      self ! Pianobot.Emote(artist match {
        case Some(x) =>
          SQLUtilities.isSongKnown(connection, x, songname) match {
            case true => knows
            case false => stumped
          }
        case None =>
          val all_artists = SQLUtilities.getArtistsWhoHavePerformed(connection, songname)
          all_artists.isEmpty match {
            case true => stumped
            case false => knows
          }
      })

    case Pianobot.EnqueueSong(speaker, songname, artist) =>
      logger.info(s"asked to queue $songname")
      getQueueSize < 10 match {
        case false =>
          logger.info("rejected song request -- queue full")
          self ! Pianobot.Emote("shakes his head no. \"Have a full set already.\"")
        case true =>
          (artist match {
            case Some(x) => Some(x)
            case None =>
              val covers = SQLUtilities.getArtistsWhoHavePerformed(connection, songname)
              covers.isEmpty match {
                case true => None
                case false => Some(scala.util.Random.shuffle(covers.toList).head)
              }
          }) match {
            case None =>
              logger.info("rejected song request -- song unknown")
              self ! Pianobot.Emote("shakes his head no.  \"Don't know it.\"")
            case Some (band) => SQLUtilities.isSongKnown(connection, band, songname) match {
              case false =>
                logger.info("rejected song request -- song unknown")
                self ! Pianobot.Emote("shakes his head no.  \"Don't know it.\"")
              case true =>
                val length = SQLUtilities.getSongLength(connection, band, songname)
                length match {
                  case Some(x) =>
                    logger.info(s"enqueing $songname by $band")
                    self ! Pianobot.Emote("hurriedly adds it to his set list.")
                    enqueue(songname, x)
                  case None =>
                    logger.info("rejected song request -- bad length")
                }
            }
          }
      }

    case Pianobot.LeaveMessageFor(speaker, target, msg) =>
      users.contains(target) match {
        case true =>
          speaker == target match {
            case true =>
              val msg1 = "says slowly, \"You want to leave a message for ... yourself.\""
              val msg2 = "calls over to Tom, \"No more booze for " + speaker + ".\""
              self ! Pianobot.Emote(msg1)
              self ! Pianobot.Emote(msg2)
            case false =>
              val msg1 = "hollers, \"Hey, " + target + "! " + speaker + "'s looking for you!\""
              self ! Pianobot.Emote(msg1)
          }
        case false =>
          SQLUtilities.leaveMessageFor(connection, speaker, target, msg) match {
            case false => self ! Pianobot.Emote("shakes his head no. \"I don't know that person.\"")
            case true => self ! Pianobot.Emote("writes it down. \"Yeah, yeah, I'll pass it on, maybe.\"")
          }
      }

    case Pianobot.WrapConnectEvent(event) =>
      logger.info(s"successfully connected to ${Environment.options("irc server")}")
      setPlayThreadShouldRun(true)

    case Pianobot.WrapConnectAttemptFailedEvent(event) =>
      logger.info(s"failed to connect to ${Environment.options("irc server")} -- aborting")
      System.exit(1)

    case Pianobot.WrapDisconnectEvent(event) =>
      for (nick <- users) SQLUtilities.setLastSeen(connection, nick)
      logger.info("server disconnected")
      if (!shuttingDown) {
        self ! "stop"
      }

    case Pianobot.WrapExceptionEvent(event) =>
      val e = event.getException
      logger.fatal(s"received exception ${e.toString}/${e.getMessage}")
      System.exit(1)

    case Pianobot.WrapNickAlreadyInUseEvent(event) =>
      logger.fatal("error -- nick already in use -- aborting")
      System.exit(1)

    case Pianobot.WrapJoinEvent(event) =>
      logger.info("received join event")
      event.getUser.getNick == Pianobot.name match {
        case true => ;
        case false =>
          val nick = event.getUser.getNick
          val capset = SQLUtilities.getCapabilitiesFor(connection, nick)
          capset.contains("admin") || capset.contains("friend") || event.getUser.isIrcop match {
            case true => self ! Pianobot.Emote(s"gives a nod of greeting to $nick.")
            case false => ;
          }
          SQLUtilities.setLastSeen(connection, nick)
          users.contains(nick) match {
            case true => ;
            case false => users += nick
          }
          checkMessagesFor(nick)
      }

    case Pianobot.WrapKickEvent(event) =>
      logger.info("received kick event -- shutting down")
      self ! "stop"

    case Pianobot.WrapMessageEvent(event) =>
      logger.info("received message event: " + event.getMessage)
      try {
        event.getUser.getNick == Pianobot.name match {
          case true => ;
          case false => Parser(true, event.getUser.getNick, event.getMessage)
        }
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapPrivateMessageEvent(event) =>
      logger.info("received private message event")
      try {
        event.getUser.getNick == Pianobot.name match {
          case true => ;
          case false => Parser(false, event.getUser.getNick, event.getMessage)
        }
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapQuitEvent(event) =>
      logger.info("received quit event")
      SQLUtilities.setLastSeen(connection, event.getUser.getNick)
      users -= event.getUser.getNick

    case Pianobot.WrapUserListEvent(event) =>
      logger.info("received user list event")
      try {
        for (i <- event.getUsers.iterator.asScala.toIterable) {
          SQLUtilities.setLastSeen(connection, i.getNick)
          checkMessagesFor(i.getNick)
          users += i.getNick
        }
      } catch {
        case e : Throwable =>
          logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}" )
          System.exit(1)
      }

    case Pianobot.WrapPartEvent(event) =>
      logger.info("received part event")
      val nick = event.getUser.getNick
      SQLUtilities.setLastSeen(connection, nick)
      if (users.contains(nick)) users -= nick

    case _ =>
      logger.info("received unknown message")
  }
}
