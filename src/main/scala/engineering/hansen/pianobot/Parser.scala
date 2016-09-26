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
  private val haveYouSeen = "^have\\s+you\\s+seen\\s+([A-Za-z0-9_]+)\\??$".r
  private var actor : Option[akka.actor.ActorRef] = None

  def setActor(a : akka.actor.ActorRef) = {
    actor = Some(a)
  }

  def sendToActor(resp: Response, alt: String) = {
    actor match {
      case Some(x) => x ! resp
      case None => println(alt)
    }
  }

  def apply(isPublic: Boolean, speaker: String, msg: String) = {
    msg match {
      case filter(x) =>
        x.trim() match {
          case playSong(_, song, _, artist) =>
            sendToActor(EnqueueSong(speaker, song, Option(artist)),
              s"enqueuing $song by ${Option(artist).getOrElse("unknown")}")
          case doYouKnow(song, _, artist) =>
            sendToActor(DoYouKnow(speaker, song, Option(artist)),
              s"checking to see if the bot knows $song by ${Option(artist).getOrElse("unknown")}")
          case leaveMessage(_, person, _, message) =>
            sendToActor(LeaveMessageFor(speaker, person, message),
              s"$speaker left message for $person: $msg")
          case goHome(_, _) =>
            sendToActor(GoHome(speaker),
              s"$speaker told $botname to go home")
          case haveYouSeen(person) =>
            sendToActor(HaveYouSeen(person),
              s"$speaker asked when $botname last saw $person")
          case _ => ;
        }
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
        "go home",
        "have you seen Alaric?",
        "have you seen Alaric"
      )) yield s"${Environment.options("bot")}, $i")
        try {
          Parser(Environment.options("admin"), j)
          Parser("dummy-user", j)
        } catch {
          case x : MatchError => println(j)
        }
   }
}
