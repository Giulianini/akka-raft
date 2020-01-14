package it.unibo.sd1920.akka_raft.server

import it.unibo.sd1920.akka_raft.model.BankStateMachine.{ApplyCommand, BankCommand}
import it.unibo.sd1920.akka_raft.model.Entry
import it.unibo.sd1920.akka_raft.protocol.{AppendEntries, AppendEntriesResult, ClientRequest, RequestVote}
import it.unibo.sd1920.akka_raft.server.ServerActor.SchedulerTick


private trait LeaderBehaviour {
  this: ServerActor =>

  private var followersStatusMap: Map[String, FollowerStatus] = Map()

  protected def leaderBehaviour: Receive = controlBehaviour orElse {
    case AppendEntriesResult(res, _) => handleAppendResult(sender().path.name, res)
    case req: ClientRequest => handleRequest(req)
    case RequestVote(candidateTerm, _, _, _) if candidateTerm > currentTerm =>
    //case StateMachineResult =>
    case SchedulerTick => heartbeatTimeout()
  }

  private def handleAppendResult(name: String, value: Boolean): Unit = name match {
    case _ => followersStatusMap = followersStatusMap + (name -> new FollowerStatus(1, 1))
  }

  private def handleRequest(req: ClientRequest): Unit = {
    val i: Option[Int] = serverLog.getIndexFromReqId(req.requestID)
    if (serverLog.isReqIdCommitted(req.requestID)) {
      stateMachineActor ! ApplyCommand(new Entry[BankCommand](req.command, currentTerm, i.get, req.requestID))
    } else if (i.isEmpty) {
      val entry = new Entry[BankCommand](req.command, currentTerm, serverLog.size, req.requestID)
      serverLog.putElementAtIndex(entry)
      logWithRole(entry.toString)
    }
  }


  private def heartbeatTimeout(): Unit = {
    servers.filter(serverRef => serverRef._2 != self).foreach(server => server._2 ! AppendEntries(currentTerm, None, None, serverLog.getCommitIndex))
  }
}

class FollowerStatus(nextIndexToSend: Int, lastMatchIndex: Int) {
  assert(nextIndexToSend >= 0)
  assert(lastMatchIndex >= 0)
}
