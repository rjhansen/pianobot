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
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._
import scala.collection.mutable
import engineering.hansen.pianobot.Utilities._
import engineering.hansen.pianobot.Pianobot._

object Pianobot {

  abstract class Response

  case class DoYouKnow(speaker: String, name: String, artist: Option[String]) extends Response

  case class KnownBy(speaker: String, bandname: String) extends Response

  case class EnqueueSong(speaker: String, name: String, artist: Option[String]) extends Response

  case class LeaveMessageFor(speaker: String, target: String, msg: String) extends Response

  case class GoHome(speaker: String) extends Response

  case class HaveYouSeen(speaker: String, who: String) extends Response

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
  private val logger = LogManager.getLogger(getClass)
  private val connection = SQLUtilities.getConnection
  private val users = new mutable.HashSet[String]()
  private val playlist = new ConcurrentLinkedQueue[(String, Int)]()
  private val shutdownFlag = new ConcurrentLinkedQueue[Boolean]()
  private val engine = new PircBotX(new Configuration.Builder()
    .setName(name)
    .setNickservPassword(password)
    .addServer(server)
    .addAutoJoinChannel(channel)
    .addListener(new PianobotListener(self))
    .setAutoReconnect(false)
    .buildConfiguration())

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

  new Thread(new Runnable() {
    override def run() = {
      while (shutdownFlag.isEmpty) {
        engine.isConnected match {
          case false => Thread.sleep(1000)
          case true => Option(playlist.peek()) match {
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

  private def startBot() =
    if (!engine.isConnected) {
      logger.info("Pianobot spinning up")
      new Thread(new Runnable {
        override def run() = {
          engine.startBot()
        }
      })
    }

  private def stopBot() = {
    shutdownFlag.add(true)
    if (engine.isConnected)
      engine.sendIRC().quitServer("went home -- normal disconnect")
    logger.info("Pianobot shutting down")
    System.exit(0)
  }

  private def haveYouSeen(speaker: String, nick: String) = speaker == nick match {
    case true =>
      self ! Send(s"Do you need a better weed dealer, $speaker, is that it?  'Cause I know a guy.")
    case false => users contains nick match {
      case true => self ! Emote("points in " + nick + "'s direction.")
      case false => SQLUtilities.getLastSeen(connection, nick) match {
        case None => self ! Emote("shrugs.  \"" + nick + "?  Whozzat?\"")
        case Some(stamp) => self ! Send(s"I last saw $nick at $stamp.")
      }
    }
  }

  private def goHome() = {
    self ! Pianobot.Emote("dutifully gathers up his music and goes home.")
    self ! "stop"
  }

  private def doYouKnow(speaker: String, songname: String, artist: Option[String]) = {
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
  }

  private def enqueueSong(speaker: String, songname: String, artist: Option[String]) = {
    logger.info(s"asked to queue $songname")
    playlist.size() <= 5 match {
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
          case Some(band) => SQLUtilities.isSongKnown(connection, band, songname) match {
            case false =>
              logger.info("rejected song request -- song unknown")
              self ! Pianobot.Emote("shakes his head no.  \"Don't know it.\"")
            case true =>
              val length = SQLUtilities.getSongLength(connection, band, songname)
              length match {
                case Some(x) =>
                  logger.info(s"enqueing $songname by $band")
                  self ! Pianobot.Emote("hurriedly adds it to his set list.")
                  playlist.add((songname, x))
                case None =>
                  logger.info("rejected song request -- bad length")
              }
          }
        }
    }
  }

  private def leaveMessageFor(speaker: String, target: String, msg: String) = target == name match {
    case true =>
      self ! Emote("looks confused. \"But I'm right here!\"")
    case false => speaker == target match {
      case true =>
        val msg1 = "says slowly, \"You want to leave a message for ... yourself.\""
        val msg2 = "calls over to Tom, \"No more booze for " + speaker + ".\""
        self ! Emote(msg1)
        self ! Emote(msg2)
      case false => users.contains(target) match {
        case true =>
          val msg1 = "hollers, \"Hey, " + target + "! " + speaker + "'s looking for you!\""
          self ! Emote(msg1)
        case false => SQLUtilities.leaveMessageFor(connection, speaker, target, msg) match {
          case false => self ! Emote("shakes his head no. \"I don't know that person.\"")
          case true => self ! Emote("writes it down. \"Yeah, yeah, I'll pass it on, maybe.\"")
        }
      }
    }
  }

  private def userJoined(event: JoinEvent) = {
    logger.info("received join event")
    event.getUser.getNick == name match {
      case true => ;
      case false =>
        val nick = event.getUser.getNick
        val capset = SQLUtilities.getCapabilitiesFor(connection, nick)
        capset.contains("admin") || capset.contains("friend") || event.getUser.isIrcop match {
          case true => self ! Pianobot.Emote(s"gives a nod of greeting to $nick.")
          case false => ;
        }
        SQLUtilities.setLastSeen(connection, nick)
        users += nick
    }
  }

  private def receivedMessage(event: MessageEvent) = {
    logger.info("received message event: " + event.getMessage)
    try {
      event.getUser.getNick == name match {
        case true => ;
        case false => Parser(true, event.getUser.getNick, event.getMessage)
      }
    } catch {
      case e: Throwable =>
        logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}")
        System.exit(1)
    }
  }

  private def receivedPrivateMessage(event: PrivateMessageEvent) = try {
    logger.info("received private message event: " + event.getMessage)
    event.getUser.getNick == name match {
      case true => ;
      case false => Parser(false, event.getUser.getNick, event.getMessage)
    }
  } catch {
    case e: Throwable =>
      logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}")
      System.exit(1)
  }


  private def userQuit(nick: String) = {
    logger.info(s"received quit event for $nick")
    name == nick match {
      case true => ;
      case false =>
        SQLUtilities.setLastSeen(connection, nick)
        users -= nick
    }
  }

  private def receivedUserList(nicks: Iterable[String]) = try {
    logger.info("received user list event")
    users.clear()
    for (nick <- nicks if nick != name) {
      SQLUtilities.setLastSeen(connection, nick)
      val m = SQLUtilities.getMessagesFor(connection, nick)
      m match {
        case None => ;
        case Some(messages) =>
          engine.sendIRC.message(nick, "So, people left some messages for you.  All times are UTC.")
          for ((source, communique, seconds) <- messages) {
            val stamp = timestampToRFC1123(seconds)
            engine.sendIRC.message(nick, s"$stamp | $source | $communique")
          }
          SQLUtilities.flushMessagesFor(connection, nick)
      }
      users += nick
    }
  } catch {
    case e: Throwable =>
      logger.fatal(s"unhandled exception: ${e.toString}/${e.getMessage}")
      System.exit(1)
  }

  private def userParted(nick: String) = {
    logger.info("received part event")
    nick == name match {
      case true => ;
      case false =>
        SQLUtilities.setLastSeen(connection, nick)
        if (users.contains(nick)) users -= nick
    }
  }


  def receive = {
    case "start" => startBot()

    case "stop" => stopBot()

    case Send(msg) => engine.sendIRC().message(channel, msg)

    case Emote(msg) => engine.sendIRC().action(channel, msg)

    case HaveYouSeen(speaker, nick) => haveYouSeen(speaker, nick)

    case GoHome(speaker) => goHome()

    case DoYouKnow(speaker, songname, artist) => doYouKnow(speaker, songname, artist)

    case EnqueueSong(speaker, songname, artist) => enqueueSong(speaker, songname, artist)

    case LeaveMessageFor(speaker, target, msg) => leaveMessageFor(speaker, target, msg)

    case WrapConnectEvent(event) => logger.info(s"successfully connected to $server")

    case WrapConnectAttemptFailedEvent(event) =>
      logger.info(s"failed to connect to $server -- aborting")
      System.exit(1)

    case WrapDisconnectEvent(event) =>
      for (nick <- users) SQLUtilities.setLastSeen(connection, nick)
      logger.info("server disconnected")

    case WrapExceptionEvent(event) =>
      val e = event.getException
      logger.fatal(s"received exception ${
        e.toString
      }/${
        e.getMessage
      }")
      System.exit(1)

    case WrapNickAlreadyInUseEvent(event) =>
      logger.fatal("error -- nick already in use -- aborting")
      System.exit(1)

    case WrapJoinEvent(event) => userJoined(event)

    case WrapKickEvent(event) =>
      logger.info("received kick event -- shutting down")
      self ! "stop"

    case WrapMessageEvent(event) => receivedMessage(event)

    case WrapPrivateMessageEvent(event) => receivedPrivateMessage(event)

    case WrapQuitEvent(event) => userQuit(event.getUser.getNick)

    case WrapUserListEvent(event) =>
      receivedUserList((for (i <- event.getUsers.iterator.asScala.toIterable) yield i.getNick).toSet - name)

    case WrapPartEvent(event) => userParted(event.getUser.getNick)

    case _ =>
      logger.info("received unknown message")
  }
}
