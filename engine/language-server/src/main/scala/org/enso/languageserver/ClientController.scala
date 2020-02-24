package org.enso.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.enso.languageserver.ClientApi._
import org.enso.languageserver.data.{CapabilityRegistration, Client}
import org.enso.languageserver.filemanager.FileManagerApi.{
  FileWrite,
  FileWriteParams
}
import org.enso.languageserver.filemanager.FileManagerProtocol.FileOperationResult
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  FileSystemFailure
}
import org.enso.languageserver.jsonrpc.Errors.UnknownError
import org.enso.languageserver.jsonrpc._

import scala.concurrent.duration._

/**
  * The JSON RPC API provided by the language server.
  * See [[https://github.com/luna/enso/blob/master/doc/design/engine/engine-services.md]]
  * for message specifications.
  */
object ClientApi {
  import io.circe.generic.auto._

  case object AcquireCapability extends Method("capability/acquire") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = CapabilityRegistration
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case class ReleaseCapabilityParams(id: UUID)

  case object ReleaseCapability extends Method("capability/release") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = ReleaseCapabilityParams
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ForceReleaseCapability
      extends Method("capability/forceReleased") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = ReleaseCapabilityParams
    }
  }

  case object GrantCapability extends Method("capability/granted") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = CapabilityRegistration
    }
  }

  val protocol: Protocol = Protocol.empty
    .registerRequest(AcquireCapability)
    .registerRequest(ReleaseCapability)
    .registerRequest(FileWrite)
    .registerNotification(ForceReleaseCapability)
    .registerNotification(GrantCapability)

  case class WebConnect(webActor: ActorRef)
}

/**
  * An actor handling communications between a single client and the language
  * server.
  *
  * @param clientId the internal client id.
  * @param server the language server actor.
  */
class ClientController(val clientId: Client.Id, val server: ActorRef)
    extends Actor
    with Stash
    with ActorLogging {

  import context.dispatcher

  implicit val timeout = Timeout(10.seconds)

  override def receive: Receive = {
    case ClientApi.WebConnect(webActor) =>
      unstashAll()
      context.become(connected(webActor))
    case _ => stash()
  }

  def connected(webActor: ActorRef): Receive = {
    case MessageHandler.Disconnected =>
      server ! LanguageProtocol.Disconnect(clientId)
      context.stop(self)

    case LanguageProtocol.CapabilityForceReleased(id) =>
      webActor ! Notification(
        ForceReleaseCapability,
        ReleaseCapabilityParams(id)
      )

    case LanguageProtocol.CapabilityGranted(registration) =>
      webActor ! Notification(GrantCapability, registration)

    case Request(AcquireCapability, id, registration: CapabilityRegistration) =>
      server ! LanguageProtocol.AcquireCapability(clientId, registration)
      sender ! ResponseResult(AcquireCapability, id, Unused)

    case Request(ReleaseCapability, id, params: ReleaseCapabilityParams) =>
      server ! LanguageProtocol.ReleaseCapability(clientId, params.id)
      sender ! ResponseResult(ReleaseCapability, id, Unused)

    case Request(FileWrite, id, params: FileWriteParams) =>
      server
        .ask(FileManagerProtocol.FileWrite(params.path, params.content))
        .mapTo[FileOperationResult]
        .map(_ -> id)
        .pipeTo(self)

    case (FileOperationResult(Left(FileSystemFailure(reason))), id: Id) =>
      webActor ! ResponseError(Some(id), UnknownError(1000, reason))

    case (FileOperationResult(Right(())), id: Id) =>
      webActor ! ResponseResult(FileWrite, id, Unused)
  }

}
