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

import engineering.hansen.pianobot.Pianobot._

object Parser {
  private val botname = Environment.options("bot")
  private val filter = ("\\s*" + botname + ",?\\s*(.*)$").r
  private val playSong = "^(please\\s+)?play\\s+\"([A-Za-z0-9_' -]+)\"(\\s+by\\s+([^.]*))?\\.?$".r
  private val doYouKnow = "^do\\s+you\\s+know\\s\"([A-Za-z0-9_' -]+)\"(\\s+by\\s+([^?]*))?\\??$".r
  private val leaveMessage = "^(please\\s+)?tell\\s+([A-Za-z0-9_-]+)\\s+(that\\s+)?\"?([^\"\\.]+)\"?\\.?$".r
  private val goHome = "^(please\\s+)?go\\s+home(\\snow)?\\.?".r
  private var actor : Option[akka.actor.ActorRef] = None
  private var bot : Option[org.pircbotx.PircBotX] = None

  def setActor(a : akka.actor.ActorRef) = {
    actor = Some(a)
  }

  def setEngine(a : org.pircbotx.PircBotX) = {
    bot = Some(a)
  }

  private def PlaySong(speaker: String, song: String, artist: Option[String]) = {
    actor match {
      case Some(x) => ;
      case None => artist match {
        case Some(x) => println(s"queueing ${artist.get}'s $song")
        case None => println(s"queueing $song (no artist specified)")
      }
    }
  }

  private def DoYouKnow(speaker: String, song: String, artist: Option[String]) = {
    actor match {
      case Some(x) => ;
      case None => artist match {
        case Some(x) => println(s"looking up ${artist.get}'s $song")
        case None => println(s"looking up $song (no artist specified)")
      }
    }
  }

  private def LeaveMessage(speaker: String, target: String, msg: String) = {
    actor match {
      case Some(x) => ;
      case None => println(s"Received a message for $target: $msg")
    }
  }

  private def GoHome(speaker: String) = {
    val isAdmin = SQLUtilities.GetCapabilitiesFor(speaker).contains("admin")
    val msg = isAdmin match {
      case true => s"""silently gets up, pays his bar tab, and leaves."""
      case false => s"""$speaker, to misquote _Blazing Saddles_, \"Screw you, I'm workin' for ${Environment.options("admin")}!""""
    }
    actor match {
      case Some(x) => ;
      case None => println(s"$msg")
    }
  }

  def apply(speaker: String, msg: String) : Unit = {
    var rest = ""
    msg match {
      case filter(x) => rest = x.trim()
      case _ => return
    }
    rest match {
      case playSong(_, song, _, artist) => PlaySong(speaker, song, Option(artist))
      case doYouKnow(song, _, artist) => DoYouKnow(speaker, song, Option(artist))
      case leaveMessage(_, person, _, message) => LeaveMessage(speaker, person, message)
      case goHome(_, _) => GoHome(speaker)
      case _ => ;
    }
  }

    def selfTest() = {
      for (j <- for (i <- List("do you know \"Clair de Lune\" by Claude Debussy?",
        "do you know \"Clair de Lune\" by Claude Debussy",
        "do you know \"Clair de Lune\"?",
        "do you know \"Clair de Lune\"",
        "please play \"Clair de Lune\" by Claude Debussy.",
        "please play \"Clair de Lune\" by Claude Debussy",
        "please play \"Clair de Lune\".",
        "please play \"Clair de Lune\"",
        "play \"Clair de Lune\" by Claude Debussy.",
        "play \"Clair de Lune\" by Claude Debussy",
        "play \"Clair de Lune\".",
        "play \"Clair de Lune\"",
        "please tell Alaric that I'm looking for him.",
        "please tell Alaric I'm looking for him.",
        "please tell Alaric that I'm looking for him",
        "please tell Alaric I'm looking for him",
        "tell Alaric that I'm looking for him.",
        "tell Alaric I'm looking for him.",
        "tell Alaric that I'm looking for him",
        "tell Alaric I'm looking for him",
        "please tell Alaric that \"I'm looking for him\".",
        "please tell Alaric \"I'm looking for him\".",
        "please tell Alaric that \"I'm looking for him\"",
        "please tell Alaric \"I'm looking for him\"",
        "tell Alaric that \"I'm looking for him\".",
        "tell Alaric \"I'm looking for him\".",
        "tell Alaric that \"I'm looking for him\"",
        "tell Alaric \"I'm looking for him",
        "please go home now.",
        "please go home now",
        "please go home.",
        "please go home",
        "go home.",
        "go home"


      )) yield s"Pianobot_TEST, $i")
        try {
          Parser(Environment.options("admin"), j)
          Parser("dummy-user", j)
        } catch {
          case x : MatchError => println(j)
        }
   }
}
