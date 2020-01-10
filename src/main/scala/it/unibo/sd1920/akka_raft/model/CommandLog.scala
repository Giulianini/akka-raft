package it.unibo.sd1920.akka_raft.model

class CommandLog[Command](
  private var entries: List[Entry[Command]]
){
  //Log indexes
  private var commitIndex = -1
  //index ->        prevI             lastI nextI
  //index -> |  0  |  1  |  2  |  3  |  4  |     |
  //term  -> |  0  |  0  |  1  |  1  |  2  |     |
  def size: Int = this.entries.size

  def previousIndex: Int = lastIndex match {
    case 0 => 0
    case n => n - 1
  }
  def nextIndex: Int = size

  def lastTerm: Int = this.entries.lastOption.map(t => t.term).getOrElse(0)
  def lastIndex: Int = this.entries.lastOption.map(t => t.index).getOrElse(0)

  def getEntryAtIndex(index: Int): Option[Entry[Command]] = index match {
    case n if size > 0 && n < size => Some(entries(n))  //positive values
    case _ => None                                      //negative values
  }

  def committedEntries: List[Entry[Command]] = entries.slice(0, commitIndex)

  //Log Operation
  def commit(index: Int): Unit = this.commitIndex = index
  def getCommitIndex: Int = this.commitIndex
  def append(entry: Entry[Command]): Unit = entries = entries :+ entry
}

object CommandLog {
  def emptyLog[T](): CommandLog[T] = new CommandLog(List.empty)
  def populatedLog[T](initialLog: List[Entry[T]]): CommandLog[T] = new CommandLog(initialLog)
}

case class Entry[Command](
  command: Command,
  term: Int,
  index: Int,
  requestId: Long
) {
  assert(index >= 0 /* || index = -1*/ ) //Come in java
  assert(term >= 0)
}
