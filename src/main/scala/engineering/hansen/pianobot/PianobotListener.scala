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

import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events._
import org.apache.logging.log4j.LogManager
import akka.actor.ActorRef

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

class PianobotListener extends ListenerAdapter {
  private val logger = LogManager.getLogger(getClass())
  private val server = Environment.options("irc server")
  private var actor : Option[ActorRef] = None

  def setActor(b: ActorRef) = {
    actor = Some(b)
  }

  def getActor : ActorRef = {
    actor.get
  }

  override def onConnect(e: ConnectEvent) = {
    getActor ! WrapConnectEvent(e)
  }

  override def onConnectAttemptFailed(e: ConnectAttemptFailedEvent) = {
    System.exit(0)
  }

  override def onDisconnect(e: DisconnectEvent) = {
    getActor ! WrapDisconnectEvent(e)
  }

  override def onException(e: ExceptionEvent) = {
    System.err.println(e)
    System.err.println(e.getMessage)
    System.exit(1)
  }

  override def onNickAlreadyInUse(e: NickAlreadyInUseEvent) = {
    System.err.println("Nick already in use")
    System.exit(1)
  }

  override def onJoin(e: JoinEvent) = {
  }

  override def onKick(e: KickEvent) = {
  }

  override def onMessage(e: MessageEvent) = {
  }

  override def onPrivateMessage(e: PrivateMessageEvent) = {
  }

  override def onQuit(e: QuitEvent) = {
  }

  override def onUserList(e: UserListEvent) = {
    getActor ! WrapUserListEvent(e)
  }
}
