package it.unibo.sd1920.akka_raft.raft

import akka.actor.ActorRef
import it.unibo.sd1920.akka_raft.model.BankStateMachine.BankCommand
import it.unibo.sd1920.akka_raft.model.Entry

sealed trait RaftMessage {

  case class RequestVote(
                          candidateTerm: Int,
                          candidateId: ActorRef,
                          lastLogTerm: Int,
                          lastLogIndex: Int
                        ) extends RaftMessage{
    assert(candidateTerm >= 0)
    assert(lastLogTerm >= 0)

  }

case class RequestVoteResult(
                             voteGranted: Boolean,
                             followerTerm: Int
                            ){
    assert(followerTerm >= 0)
  }

  case  class AppendEntries(
                           leaderTerm: Int,
                           leaderID: String,
                           previousEntry: Entry[BankCommand],
                           entry: Entry[BankCommand],
                           leaderLastCommit: Int,
                           )extends RaftMessage{
    assert(leaderTerm >= 0)
    assert(leaderLastCommit >= 0)
  }

  case  class AppendEntriesResult(
                                  success: Boolean,
                                  followerTerm: Int
                                  ){
    assert(followerTerm >= 0)
  }
}
