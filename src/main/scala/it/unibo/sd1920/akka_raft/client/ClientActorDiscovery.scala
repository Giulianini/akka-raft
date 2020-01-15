package it.unibo.sd1920.akka_raft.client

import akka.cluster.ClusterEvent.{MemberDowned, MemberUp}
import akka.cluster.Member
import it.unibo.sd1920.akka_raft.client.ClientActor.{ClientIdentity, IdentifyClient, ServerIdentity}
import it.unibo.sd1920.akka_raft.server.ServerActor
import it.unibo.sd1920.akka_raft.utils.{NetworkConstants, NodeRole}

private trait ClientActorDiscovery {
  this: ClientActor =>

  protected def clusterBehaviour: Receive = {
    case MemberUp(member) => this.manageNewMember(member)
    case MemberDowned(member) => //TODO
    case IdentifyClient(NodeRole.CLIENT) => sender() ! ClientActor.ClientIdentity(self.path.name)
    case IdentifyClient(NodeRole.SERVER) => sender() ! ServerActor.ClientIdentity(self.path.name)
    case ClientIdentity(name: String) => addClient(name)
    case ServerIdentity(name: String) => addServer(name)
  }

  private def manageNewMember(member: Member): Unit = member match {
    case m if member.roles.contains("server") =>
      context.system.actorSelection(s"${m.address}/user/**") ! ServerActor.IdentifyServer(NodeRole.CLIENT);
    case m if member.roles.contains("client") =>
      context.system.actorSelection(s"${m.address}/user/**") ! ClientActor.IdentifyClient(NodeRole.CLIENT);
    case _ =>
  }

  private def addClient(name: String): Unit = {
    this.clients = this.clients + (name -> sender())
  }

  private def addServer(name: String): Unit = {
    this.servers = this.servers + (name -> sender())
    view addServer name
    if (servers.size >= NetworkConstants.numberOfServer) {
      context become (clusterBehaviour orElse onMessage)
    }
  }
}
