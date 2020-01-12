package it.unibo.sd1920.akka_raft.server


import akka.actor.ActorRef
import it.unibo.sd1920.akka_raft.raft._
import it.unibo.sd1920.akka_raft.server.ServerActor.{ClientRequest, SchedulerTick}


private trait FollowerBehaviour {
  this: ServerActor =>
  private var leaderRef : Option[ActorRef] = None

  protected def followerBehaviour: Receive =  clusterBehaviour orElse {
    case SchedulerTick => becomingCandidate()
    case requestVote: RequestVote => updateTerm(requestVote.candidateTerm)
      handleRequestVote(requestVote)
    case appendEntry: AppendEntries => leaderRef = Some(sender())
      handleAppendEntries(appendEntry)
    case ClientRequest(_,_) => sender() ! Redirect(leaderRef)
    case _ =>
  }

  def becomingCandidate(): Unit = {
    leaderRef = None
    currentTerm += 1 //?? TODO è corretto?
    context.become(candidateBehaviour)
    startTimer()
  }

  def handleRequestVote(requestVote: RequestVote): Unit =  {
    updateTerm(requestVote.candidateTerm)

    requestVote match{
      case RequestVote(candidateTerm,_,_,_) if candidateTerm < currentTerm => sender() ! RequestVoteResult(voteGranted = false, currentTerm)
      case RequestVote(_,candidateId,lastLogTerm,lastLogIndex) if (votedFor.isEmpty && checkLogBehind(lastLogTerm,lastLogIndex)) => votedFor = Some(sender().path.name)
        sender() ! RequestVoteResult(voteGranted = true, currentTerm)
      case RequestVote(candidateTerm, candidateId, lastLogTerm, lastLogIndex) => sender() ! RequestVoteResult(voteGranted = false, currentTerm)
      case _ =>
    }
  }

  def handleAppendEntries(appendEntry: AppendEntries): Unit = {
    updateTerm(appendEntry.leaderTerm)//?? TODO è corretto?

    appendEntry match{
      case AppendEntries(_,_,entry,_) if entry.isEmpty => startTimer(); sender() ! AckAppendEntries
      case AppendEntries(leaderTerm, _, _, _) if leaderTerm < currentTerm => startTimer()
        sender() ! AppendEntriesResult(false)
      case AppendEntries(_, previousEntry, entry, leaderLastCommit) if previousEntry.isEmpty => startTimer()
        callCommit(Math.min(serverLog.getCommitIndex, leaderLastCommit))
        sender() ! AppendEntriesResult(serverLog.putElementAtIndex(entry.get))
      case AppendEntries(_, previousEntry, _, _) if !serverLog.contains(previousEntry.get) => startTimer()
        sender() ! AppendEntriesResult(false)
      case AppendEntries(_, _, entry, leaderLastCommit) => callCommit(Math.min(serverLog.getCommitIndex, leaderLastCommit))
        sender() ! AppendEntriesResult(serverLog.putElementAtIndex(entry.get))
      case _ =>
    }
  }

  def updateTerm(term: Int): Unit = {
    if (term > currentTerm){
      currentTerm = term
      votedFor = None
    }
  }

  def callCommit(index: Int) {
    serverLog.commit(index)
  }

  def checkLogBehind(lastLogTerm: Int, lastLogIndex: Int): Boolean = {
    lastLogTerm >= currentTerm && lastLogIndex >= serverLog.lastIndex
  }

}
