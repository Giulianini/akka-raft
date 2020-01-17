package it.unibo.sd1920.akka_raft.protocol

import akka.actor.ActorRef
import it.unibo.sd1920.akka_raft.model.BankStateMachine.BankCommand
import it.unibo.sd1920.akka_raft.model.Entry

sealed trait RaftMessage

case class RequestVote(
  candidateTerm: Int,
  candidateId: ActorRef,
  lastLogTerm: Int,
  lastLogIndex: Int
) extends RaftMessage {
  assert(candidateTerm >= 0)
  assert(lastLogTerm >= 0)
}

case class RequestVoteResult(
  voteGranted: Boolean,
  followerTerm: Int
) {
  assert(followerTerm >= 0)
}

case class AppendEntries(
  leaderTerm: Int,
  previousEntry: Option[Entry[BankCommand]],
  entry: Option[Entry[BankCommand]],
  leaderLastCommit: Int,
) extends RaftMessage {
  assert(leaderTerm >= 0)
  assert(leaderLastCommit >= -1)
}

case class AppendEntriesResult(
  success: Boolean,
  matchIndex: Int,
  term: Int
) extends RaftMessage

case class Redirect(
  reqID: Int,
  leaderRef: Option[ActorRef]
) extends RaftMessage

case class ClientRequest(requestID: Int, command: BankCommand) extends RaftMessage

case class RequestResult(id: Int, success: Boolean, result: Option[Int]) extends RaftMessage
