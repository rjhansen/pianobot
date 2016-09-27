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

import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events._
import org.apache.logging.log4j.LogManager
import akka.actor.ActorRef
import engineering.hansen.pianobot.Pianobot._

class PianobotListener(actor: ActorRef) extends ListenerAdapter {

  override def onConnect(e: ConnectEvent) = {
    actor ! WrapConnectEvent(e)
  }

  override def onConnectAttemptFailed(e: ConnectAttemptFailedEvent) = {
    actor ! WrapConnectAttemptFailedEvent(e)
  }

  override def onDisconnect(e: DisconnectEvent) = {
    actor ! WrapDisconnectEvent(e)
  }

  override def onPart(e: PartEvent) = {
    actor ! WrapPartEvent(e)
  }

  override def onException(e: ExceptionEvent) = {
    actor ! WrapExceptionEvent(e)
  }

  override def onNickAlreadyInUse(e: NickAlreadyInUseEvent) = {
    actor ! WrapNickAlreadyInUseEvent(e)
  }

  override def onJoin(e: JoinEvent) = {
    actor ! WrapJoinEvent(e)
  }

  override def onKick(e: KickEvent) = {
    actor ! WrapKickEvent(e)
  }

  override def onMessage(e: MessageEvent) = {
    actor ! WrapMessageEvent(e)
  }

  override def onPrivateMessage(e: PrivateMessageEvent) = {
    actor ! WrapPrivateMessageEvent(e)
  }

  override def onQuit(e: QuitEvent) = {
    actor ! WrapQuitEvent(e)
  }

  override def onUserList(e: UserListEvent) = {
    actor ! WrapUserListEvent(e)
  }
}
