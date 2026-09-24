package com.eleckoi.android.feature.chat.ui.roleplay.web.document

/** Visual projections for the shared transcript DOM. Rich-content behavior stays layout-agnostic. */
internal val RoleplayTranscriptLayoutStyles = """
    .turn.role-assistant .message-body { color: var(--assistant-text); }
    .turn.role-user .message-body { color: var(--user-text); }
    .agent-footer { display: none; }

    body[data-layout="agent"],
    body[data-layout="social"] { text-shadow: none; }

    /* Agent: DeepSeek-sized reading typography and response actions, with ElecKoi identities. */
    body[data-layout="agent"] .turn {
      grid-template-columns: var(--avatar-width) minmax(0, 1fr);
      grid-template-areas:
        "portrait header"
        "body body"
        "footer footer";
      row-gap: 0;
    }
    body[data-layout="agent"] .turn.role-user {
      grid-template-columns: minmax(0, 1fr) var(--avatar-width);
      grid-template-areas:
        "header portrait"
        "body body"
        "footer footer";
    }
    body[data-layout="agent"] .portrait-lane { grid-area: portrait; }
    body[data-layout="agent"] .portrait-lane > .pager { display: none; }
    body[data-layout="agent"] .turn-main { display: contents; }
    body[data-layout="agent"] .turn-header {
      grid-area: header;
      min-height: var(--avatar-height);
      margin-bottom: var(--reply-gap);
      display: flex;
      align-items: center;
    }
    body[data-layout="agent"] .name {
      padding: 0;
      transition: none;
    }
    body[data-layout="agent"] .tools,
    body[data-layout="social"] .tools { display: none; }
    body[data-layout="agent"] .message-body { grid-area: body; min-width: 0; }
    body[data-layout="agent"] .turn.role-user .name { text-align: right; }
    body[data-layout="agent"] .turn.role-user .message-body {
      width: fit-content;
      max-width: min(88%, 720px);
      justify-self: end;
      padding: 9px 12px;
      border-radius: var(--bubble-radius);
      background: var(--user-bubble);
    }
    body[data-layout="agent"].assistant-bubbles .turn.role-assistant .message-body {
      width: 100%;
      padding: 9px 12px;
      border-radius: var(--bubble-radius);
      background: var(--assistant-bubble);
    }
    body[data-layout="agent"] .agent-footer:not([hidden]),
    body[data-layout="social"] .agent-footer:not([hidden]) {
      grid-area: footer;
      width: 100%;
      height: 30px;
      margin-top: 10px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: var(--muted);
      text-shadow: none;
    }
    body[data-layout="agent"] .agent-footer-leading,
    body[data-layout="social"] .agent-footer-leading {
      min-width: 0;
      height: 30px;
      display: flex;
      align-items: center;
      gap: 16px;
    }
    body[data-layout="agent"] .agent-action,
    body[data-layout="agent"] .agent-pager-action,
    body[data-layout="social"] .agent-action,
    body[data-layout="social"] .agent-pager-action {
      position: relative;
      flex: 0 0 auto;
      width: 24px;
      height: 30px;
      padding: 2px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    body[data-layout="agent"] .agent-action::before,
    body[data-layout="agent"] .agent-pager-action::before,
    body[data-layout="agent"] .agent-pager-index::before,
    body[data-layout="social"] .agent-action::before,
    body[data-layout="social"] .agent-pager-action::before,
    body[data-layout="social"] .agent-pager-index::before {
      content: "";
      position: absolute;
      inset: -7px -2px;
    }
    body[data-layout="agent"] .agent-action:disabled,
    body[data-layout="agent"] .agent-pager-action:disabled,
    body[data-layout="social"] .agent-action:disabled,
    body[data-layout="social"] .agent-pager-action:disabled {
      color: var(--soft);
      opacity: .42;
    }
    body[data-layout="agent"] .agent-action[data-action="edit"] > .icon,
    body[data-layout="social"] .agent-action[data-action="edit"] > .icon {
      width: 16px;
      height: 16px;
      opacity: .78;
    }
    body[data-layout="agent"] .turn.role-user .message-body.user-editable,
    body[data-layout="social"] .turn.role-user .message-body.user-editable {
      cursor: pointer;
    }
    body[data-layout="agent"] .turn.role-user .message-body.user-editable:focus-visible,
    body[data-layout="social"] .turn.role-user .message-body.user-editable:focus-visible {
      outline: 2px solid var(--accent);
      outline-offset: 2px;
    }
    body[data-layout="agent"] .agent-opening-pager,
    body[data-layout="social"] .agent-opening-pager {
      flex: 0 0 auto;
      height: 30px;
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }
    body[data-layout="agent"] .agent-pager-index,
    body[data-layout="social"] .agent-pager-index {
      position: relative;
      min-width: 34px;
      height: 30px;
      padding: 0 2px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font: 400 15px/18px sans-serif;
      font-variant-numeric: tabular-nums;
      letter-spacing: 0;
      white-space: nowrap;
    }

    /* Social uses a compact messaging-app row: avatar and bubble share the same top edge. */
    body[data-layout="social"] .turn {
      grid-template-columns: var(--avatar-width) minmax(0, 1fr);
      align-items: start;
    }
    body[data-layout="social"] .turn.role-user {
      grid-template-columns: minmax(0, 1fr) var(--avatar-width);
    }
    body[data-layout="social"] .turn.role-user .portrait-lane { grid-column: 2; grid-row: 1; }
    body[data-layout="social"] .turn.role-user .turn-main { grid-column: 1; grid-row: 1; }
    body[data-layout="social"] .turn.role-assistant .turn-main {
      width: fit-content;
      max-width: 88%;
    }
    body[data-layout="social"] .turn-header { display: none; }
    body[data-layout="social"] .message-body {
      width: fit-content;
      max-width: 88%;
      padding: 9px 12px 9px 18px;
      border-radius: 0;
      background: var(--assistant-bubble);
      overflow: hidden;
      clip-path: polygon(
        10px 0,
        calc(100% - 6px) 0,
        calc(100% - 2px) 2px,
        100% 6px,
        100% calc(100% - 6px),
        calc(100% - 2px) calc(100% - 2px),
        calc(100% - 6px) 100%,
        10px 100%,
        8px calc(100% - 2px),
        6px calc(100% - 6px),
        6px var(--social-tail-bottom),
        0 var(--social-tail-center),
        6px var(--social-tail-top),
        6px 6px,
        8px 2px
      );
    }
    body[data-layout="social"] .turn.role-assistant .message-body {
      max-width: 100%;
    }
    body[data-layout="social"]:not(.assistant-bubbles) .turn.role-assistant .message-body {
      padding: 0; background: transparent;
    }
    body[data-layout="social"] .turn.role-user .message-body {
      margin-left: auto;
      padding: 9px 18px 9px 12px;
      background: var(--user-bubble);
      clip-path: polygon(
        6px 0,
        calc(100% - 10px) 0,
        calc(100% - 8px) 2px,
        calc(100% - 6px) 6px,
        calc(100% - 6px) var(--social-tail-top),
        100% var(--social-tail-center),
        calc(100% - 6px) var(--social-tail-bottom),
        calc(100% - 6px) calc(100% - 6px),
        calc(100% - 8px) calc(100% - 2px),
        calc(100% - 10px) 100%,
        6px 100%,
        2px calc(100% - 2px),
        0 calc(100% - 6px),
        0 6px,
        2px 2px
      );
    }
"""
