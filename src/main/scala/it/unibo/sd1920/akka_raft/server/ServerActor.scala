package it.unibo.sd1920.akka_raft.server

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberDowned, MemberUp}
import akka.dispatch.ControlMessage
import com.typesafe.config.ConfigFactory
import it.unibo.sd1920.akka_raft.model.{BankStateMachine, CommandLog, ServerVolatileState}
import it.unibo.sd1920.akka_raft.model.Bank.BankTransactionResult
import it.unibo.sd1920.akka_raft.model.BankStateMachine.{ApplyCommand, BankCommand}
import it.unibo.sd1920.akka_raft.protocol.GuiControlMessage._
import it.unibo.sd1920.akka_raft.protocol.RaftMessage
import it.unibo.sd1920.akka_raft.server.ServerActor.{InternalMessage, SchedulerTick, SchedulerTickKey}
import it.unibo.sd1920.akka_raft.utils.{NetworkConstants, RaftConstants, RandomUtil, ServerRole}
import it.unibo.sd1920.akka_raft.utils.NodeRole.NodeRole
import it.unibo.sd1920.akka_raft.utils.ServerRole.ServerRole

import scala.concurrent.duration._
import scala.util.Random

private class ServerActor extends Actor with ServerActorDiscovery with LeaderBehaviour with CandidateBehaviour with FollowerBehaviour with ActorLogging with Timers {
  protected[this] val SERVERS_MAJORITY: Int = (NetworkConstants.numberOfServer / 2) + 1
  protected[this] val cluster: Cluster = Cluster(context.system)
  protected[this] var servers: Map[String, ActorRef] = Map()
  protected[this] var clients: Map[String, ActorRef] = Map()
  protected[this] var stateMachineActor: ActorRef = _
  protected[this] var currentRole: ServerRole = ServerRole.FOLLOWER
  protected[this] var currentTerm: Int = 0
  protected[this] var lastApplied: Int = 0
  protected[this] var lastMatched: Int = 0
  protected[this] var stopped: Boolean = false
  protected[this] var votedFor: Option[String] = None
  protected[this] val serverLog: CommandLog[BankCommand] = CommandLog.emptyLog()
  protected[this] var messageLoseThreashold: Double = 1.0
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp], classOf[MemberDowned])
    cluster.registerOnMemberUp({
    })
    stateMachineActor = context.actorOf(BankStateMachine.props(RandomUtil.randomBetween(RaftConstants.minimumStateMachineExecutionTime,
      RaftConstants.maximumStateMachineExecutionTime).millis), "StateMachine")
  }


  case class MessageInterceptor(receiver: Receive) extends Receive {
    def apply(msg: Any): Unit = {
      clients.last._2 ! GuiServerState(ServerVolatileState(currentRole, serverLog.getCommitIndex, lastApplied, votedFor, currentTerm, serverLog.nextIndex, lastMatched, serverLog.getEntries)) //TODO da cancellare

      receiver.apply(msg)
      clients.last._2 ! GuiServerState(ServerVolatileState(currentRole, serverLog.getCommitIndex, lastApplied, votedFor, currentTerm, serverLog.nextIndex, lastMatched, serverLog.getEntries)) //TODO da cancellare
    }
    def isDefinedAt(msg: Any): Boolean = {
      clients.last._2 ! GuiServerState(ServerVolatileState(currentRole, serverLog.getCommitIndex, lastApplied, votedFor, currentTerm, serverLog.nextIndex, lastMatched, serverLog.getEntries)) //TODO da cancellare

      if ((stopped || Random.nextDouble() > messageLoseThreashold && (!classOf[InternalMessage].isAssignableFrom(msg.getClass))) && (!classOf[ControlMessage].isAssignableFrom(msg.getClass))) {
        logWithRole("Messaggio bloccato:: " + msg.toString
        )
        return false
      }

      clients.last._2 ! GuiServerState(ServerVolatileState(currentRole, serverLog.getCommitIndex, lastApplied, votedFor, currentTerm, serverLog.nextIndex, lastMatched, serverLog.getEntries)) //TODO da cancellare

      receiver.isDefinedAt(msg)
    }
  }

  override def receive: Receive = clusterDiscoveryBehaviour

  protected def controlBehaviour: Receive = clusterDiscoveryBehaviour orElse {
    case GuiStopServer(_) => stopped = true
      logWithRole("\n\t\tStopped:")
    case GuiResumeServer(_) => stopped = false
      logWithRole("\n\t\tReasume:")
    case GuiTimeoutServer(_) => handleRequestTimeOut()
    case GuiMsgLossServer(_, loss) => logWithRole("\n\t\tvalore:" + loss)
      messageLoseThreashold = loss
  }

  private def handleRequestTimeOut(){
    self ! SchedulerTick
    currentRole match {
      case ServerRole.FOLLOWER => startTimeoutTimer()
      case ServerRole.CANDIDATE => startTimeoutTimer()
      case ServerRole.LEADER => startHeartbeatTimer()
    }
  }

  protected def startTimeoutTimer(): Unit = {
    timers startTimerWithFixedDelay(SchedulerTickKey, SchedulerTick, RandomUtil.randomBetween(RaftConstants.minimumTimeout, RaftConstants.maximumTimeout) millis)
  }

  protected def startHeartbeatTimer(): Unit = {
    timers startTimerWithFixedDelay(SchedulerTickKey, SchedulerTick, RaftConstants.heartbeatTimeout millis)
  }

  protected def broadcastMessage(raftMessage: RaftMessage): Unit = {
    servers.filter(s => s._2 != self).foreach(v => v._2 ! raftMessage)
  }

  protected def callCommit(index: Int): Unit = {
    val lastCommitted: Int = serverLog.getCommitIndex
    serverLog.commit(index)
    serverLog.getEntriesBetween(lastCommitted, index).foreach(e => stateMachineActor ! ApplyCommand(e))
    lastApplied = index
  }

  protected def checkElectionRestriction(lastLogTerm: Int, lastLogIndex: Int): Boolean = {
    lastLogTerm >= serverLog.lastTerm && lastLogIndex >= serverLog.lastIndex
  }

  protected def logWithRole(msg: String): Unit = log info s"${self.path.name}:$currentRole -> $msg"
}

object ServerActor {
  //MESSAGES TO SERVER
  case class IdentifyServer(senderRole: NodeRole) extends ControlMessage
  case class ServerIdentity(name: String) extends ControlMessage
  case class ClientIdentity(name: String) extends ControlMessage

  //FROM STATE MACHINE
  sealed trait InternalMessage
  case class StateMachineResult(indexAndResult: (Int, BankTransactionResult)) extends InternalMessage
  //FROM SELF
  case object SchedulerTick extends InternalMessage
  private case object SchedulerTickKey

  def props: Props = Props(new ServerActor())

  def main(args: Array[String]): Unit = {
    val name = args(0)
    val port = if (args.length == 1) "0" else args(1)

    val config = ConfigFactory.parseString(s"""akka.remote.artery.canonical.port=$port""")
      .withFallback(ConfigFactory.load("server"))
    val system = ActorSystem(NetworkConstants.clusterName, config)
    system actorOf(ServerActor.props, name)
  }
}
