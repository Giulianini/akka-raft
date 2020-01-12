package it.unibo.sd1920.akka_raft.view.screens

import com.jfoenix.controls.JFXComboBox
import eu.hansolo.enzo.led.Led
import it.unibo.sd1920.akka_raft.model.ServerVolatileState
import it.unibo.sd1920.akka_raft.view.utilities.{JavafxEnums, ViewUtilities}
import javafx.fxml.FXML
import javafx.scene.control.{Label, ScrollPane}
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.layout.{BorderPane, HBox, VBox}
import org.kordamp.ikonli.ionicons.Ionicons

trait View {
  def log(message: String): Unit
}

abstract class AbstractMainScreenView extends View {
  @FXML protected var mainBorder: BorderPane = _
  @FXML protected var vBoxServerNames: VBox = _
  @FXML protected var vBoxServerLogs: VBox = _
  @FXML protected var serverStateCombo: JFXComboBox[String] = _
  //STATE LABELS
  @FXML protected var stateLabelRole: Label = _
  @FXML protected var stateLabelLastApplied: Label = _
  @FXML protected var stateLabelVotedFor: Label = _
  @FXML protected var stateLabelCurrentTerm: Label = _
  @FXML protected var stateLabelNextIndex: Label = _
  @FXML protected var stateLabelLastMatched: Label = _

  type HBoxServerID = HBox
  type HBoxServerLog = HBox
  protected var serverToHBox: Map[String, (HBoxServerID, HBoxServerLog)] = Map()
  protected var serverToState: Map[String, ServerVolatileState] = Map()

  @FXML def initialize(): Unit = {
    this.assertNodeInjected()
    this.initCombos()
  }

  private def assertNodeInjected(): Unit = {
    assert(mainBorder != null, "fx:id=\"mainBorder\" was not injected: check your FXML file 'MainScreen.fxml'.")
    assert(vBoxServerNames != null, "fx:id=\"vBoxServerNames\" was not injected: check your FXML file 'MainScreen.fxml'.")
    assert(vBoxServerLogs != null, "fx:id=\"vBoxServerLogs\" was not injected: check your FXML file 'MainScreen.fxml'.")
    assert(serverStateCombo != null, "fx:id=\"serverStateCombo\" was not injected: check your FXML file 'MainScreen.fxml'.")
  }

  protected def showPopupInfo(): Unit = {
    ViewUtilities.showNotificationPopup("Help", "Click '^' and create a configuration \nRight Click on screen hides toolbar",
      JavafxEnums.LONG_DURATION, JavafxEnums.INFO_NOTIFICATION, null)
  }

  private def initCombos(): Unit = {
    this.serverStateCombo.getSelectionModel.selectedItemProperty()
      .addListener((_, _, newState) => {
        serverToState.get(newState) match {
          case None => ViewUtilities.showNotificationPopup("Server Errorr", "No server update present", JavafxEnums.MEDIUM_DURATION, JavafxEnums.ERROR_NOTIFICATION, null)
          case Some(state) => //TODO
        }
      })
  }

  def addServersToMap(serverID: String): Unit = {
    //ID NODE
    var serverIDNode = new HBox()
    var labelIDNode = new Label(serverID)
    labelIDNode.setGraphic(ViewUtilities.iconSetter(Ionicons.ION_CUBE, JavafxEnums.BIGGER_ICON))
    serverIDNode.getChildren.add(labelIDNode)
    //LOG NODE
    var serverLogNode = new HBox()
    serverLogNode.setMinHeight(50)
    //ADDING
    var scrollPaneLogNode = new ScrollPane()
    scrollPaneLogNode.setMinHeight(JavafxEnums.BIGGER_ICON.dim)
    scrollPaneLogNode.setVbarPolicy(ScrollBarPolicy.NEVER)
    scrollPaneLogNode.setHbarPolicy(ScrollBarPolicy.NEVER)
    scrollPaneLogNode.setPannable(true)
    scrollPaneLogNode.setFitToHeight(true)
    scrollPaneLogNode.setContent(serverLogNode)
    this.vBoxServerNames.getChildren.add(serverIDNode)
    this.vBoxServerLogs.getChildren.add(serverLogNode)
    this.serverToHBox = this.serverToHBox + (serverID -> (serverIDNode, serverLogNode))
  }

  protected def addServerToCombos(serverID: String): Unit = {
    this.serverStateCombo.getItems.add(serverID)
  }

  protected def updateServerState(serverID: String, serverVolatileState: ServerVolatileState): Unit = {
    serverToHBox.get(serverID) match {
      case None => new IllegalStateException("You sent a serverID that does not exist in map")
      case Some(entry) =>
        //UPDATING INFO
        this.serverToState = this.serverToState + (serverID -> serverVolatileState)
        //UPDATING LOG
        serverVolatileState.commandLog.getEntries.map(_.command).foreach(c => {
          val entryToAdd = new EntryBox(s"${serverVolatileState.currentTerm}:$c")
          entry._2.getChildren.add(entryToAdd)
        })
    }
  }
}

class EntryBox(info: String) extends VBox {
  val entryLed = new Led()
  val entryInfo = new Label(info)
  this.getChildren.addAll(entryLed, entryInfo)
}
