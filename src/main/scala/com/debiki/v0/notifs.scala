/**
 * Copyright (c) 2012 Kaj Magnus Lindberg (born 1979)
 */

package com.debiki.v0

import java.{util => ju}
import Prelude._


/**
 * A notification of a page action.
 *
 * @param ctime When the notification was created
 * @param recipientUserId To whom the notification should be sent
 * @param pageTitle The titel of the page where the event happened
 * @param pageId
 * @param eventType The type of the event
 * @param eventActionId The id of the event
 * @param targetActionId An action that the event-action acted upon
 * @param recipientActionId The action the recipient made, that is the reason
 *  s/he is to be notified.
 */
case class NotfOfPageAction(
  ctime: ju.Date,
  recipientUserId: String,
  pageTitle: String,
  pageId: String,
  eventType: NotfOfPageAction.Type,
  eventActionId: String,
  targetActionId: Option[String],
  recipientActionId: String,
  recipientUserDispName: String,
  eventUserDispName: String,
  targetUserDispName: Option[String],
  /** True iff an email should be created and sent. */
  emailPending: Boolean,
  /** An email that informs the recipient about this event.
  Might not yet have been sent. */
  emailId: Option[String] = None,
  debug: Option[String] = None) {

  assErrIf(targetActionId.isDefined != targetUserDispName.isDefined, "DwE8Xd2")
  assErrIf(eventType == NotfOfPageAction.Type.PersonalReply && (
    targetActionId.isDefined || targetUserDispName.isDefined), "DwE09Kb35")
  // If the email is to be created and sent, then it must not already exist.
  require(!emailPending || emailId.isEmpty)

  def recipientRoleId: Option[String] =
    if (recipientUserId startsWith "-") None else Some(recipientUserId)

  def recipientIdtySmplId: Option[String] =
    if (recipientUserId startsWith "-") Some(recipientUserId drop 1) else None
}


object NotfOfPageAction {
  sealed abstract class Type
  object Type {
    case object PersonalReply extends Type
  }
}


/**
 * Notifications and email addresses, needed to mail out the notfs.
 */
case class NotfsToMail(
  notfsByTenant: Map[String, Seq[NotfOfPageAction]],
  usersByTenantAndId: Map[(String, String), User])


object Notification {


  def calcFrom(user: User, adding: Seq[Action], to: Debate)
        : Seq[NotfOfPageAction] = {

    val actions = adding
    val page = to

    def _makeNotfForUserRepliedTo(post: Post): List[NotfOfPageAction] = {
      val postRepliedTo = page.vipo(post.parent) getOrElse {
        // This might be a page config/template Post, and, if so,
        // it has no parent.
        return Nil
      }
      val userRepliedTo = postRepliedTo.user_!
      if (user.id == Some(userRepliedTo.id)) {
        // Don't notify the user of his/her own replies.
        Nil
      }
      else if (post.tyype == PostType.Text) {
        List(NotfOfPageAction(
          ctime = post.ctime,
          recipientUserId = userRepliedTo.id,
          pageTitle = page.titleText.getOrElse("Unnamed page"),
          pageId = page.id,
          eventType = NotfOfPageAction.Type.PersonalReply,
          eventActionId = post.id,
          targetActionId = None,
          recipientActionId = postRepliedTo.id,
          recipientUserDispName = userRepliedTo.displayName,
          eventUserDispName = user.displayName,
          targetUserDispName = None,
          emailPending =
             userRepliedTo.emailNotfPrefs == EmailNotfPrefs.Receive))
      } else {
        // Currently not supported.
        // Need to make loadInboxItem understand DW1_PAGE_ACTIONS = 'Tmpl',
        // and should render Template suggestions differently from
        // normal replies? The link should open the suggestion
        // on a new page, with the template as root post?
        Nil
      }
    }

    val seeds: Seq[NotfOfPageAction] = actions flatMap (_ match {
      case post: Post =>
        _makeNotfForUserRepliedTo(post)
      // Note: If you add notfs (below) for other things than replies,
      // then, in debiki-app-play, update Mailer._constructEmail.notfToHtml
      // so it generates correct URL anchor links to that stuff.
      // Currently notfToHtml assumes all notfs are for replies and
      // writes #post-<id> anchors only.
      case e: Edit =>
        Nil  // fix later, see note above
      case app: EditApp =>
        Nil  // fix later, see note above
      case flag: Flag =>
        Nil  // fix later, see note above
      case _ =>
        Nil  // skip for now
    })

    // val pageAuthorInboxSeed = ...
    // val moderatorInboxSeed = ...
    seeds  // ++ pageAuthorInboxSeed ++ moderatorInboxSeed
  }

}



object Email {

  def apply(sendTo: String, subject: String, bodyHtmlText: String): Email =
    Email(
      id = _generateId(),
      sentTo = sendTo,
      sentOn = None,
      subject = subject,
      bodyHtmlText = bodyHtmlText,
      providerEmailId = None,
      failureText = None)

  /**
   * The email id should be a random value, so it cannot be guessed,
   * because it's a key in unsubscribe URLs.
   */
  private def _generateId(): String = nextRandomString() take 8
}


case class Email(
  id: String,
  sentTo: String,
  sentOn: Option[ju.Date],
  subject: String,
  bodyHtmlText: String,
  providerEmailId: Option[String],
  failureText: Option[String] = None)


// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list

