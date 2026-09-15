package com.eleckoi.android.feature.chat.ui.roleplay.web.document

internal val RoleplayTranscriptStyles = """
    :root {
      color-scheme: dark;
      --text: #f5f5f2;
      --body-text: #f5f5f2;
      --italic-text: #c4def5;
      --underline-text: #d6e7f9;
      --quote-text: #cbe1fb;
      --inline-code-text: #ddeaf7;
      --muted: #b8b8b2;
      --soft: #92928d;
      --accent: #b7d8ff;
      --panel: #3c3c3c;
      --line: rgba(255,255,255,.18);
      --jump-surface: rgba(36,36,37,.96);
      --avatar-background: rgba(255,255,255,.075);
      --avatar-placeholder: rgba(255,255,255,.48);
      --code-foreground: #f3f1ec;
      --code-background: #232323;
      --code-border: rgba(255,255,255,.18);
      --code-header: #232323;
      --font-size: 14px;
      --line-height: 19.6px;
      --letter-spacing: 0px;
      --paragraph-gap: 6px;
      --name-size: 15px;
      --name-line-height: 20.25px;
      --avatar-width: 55px;
      --avatar-height: 73.33px;
      --avatar-radius: 8px;
      --avatar-gap: 10px;
      --horizontal-padding: 10px;
      --reply-gap: 4px;
      --turn-gap: 5px;
      --roleplay-text-shadow: 0 0 1px rgba(0,0,0,.3);
    }
    * { box-sizing: border-box; }
    html, body { min-height: 100%; margin: 0; background: transparent; overflow-anchor: none; }
    html { overflow-y: auto; overscroll-behavior-y: contain; scrollbar-width: none; }
    html::-webkit-scrollbar { display: none; }
    body {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      color: var(--body-text);
      font-family: sans-serif;
      font-size: var(--font-size);
      line-height: var(--line-height);
      letter-spacing: var(--letter-spacing);
      text-shadow: var(--roleplay-text-shadow);
      -webkit-text-size-adjust: 100%;
      overflow-wrap: anywhere;
    }
    button, input { font: inherit; }
    button {
      appearance: none; -webkit-appearance: none; margin: 0; padding: 0; border: 0;
      border-radius: 0; background: transparent; color: inherit;
      -webkit-tap-highlight-color: transparent; touch-action: manipulation;
    }
    #top-spacer, #bottom-spacer, #content-tail-gap {
      width: 1px; pointer-events: none;
    }
    #top-spacer { flex: 1 0 auto; min-height: 0; }
    #bottom-spacer { flex: 0 0 auto; height: 0; }
    #content-tail-gap { flex: 0 0 10px; height: 10px; }
    #turns, #empty { flex: 0 0 auto; width: 100%; }
    .turn {
      display: grid;
      grid-template-columns: var(--avatar-width) minmax(0, 1fr);
      column-gap: var(--avatar-gap);
      padding: 8px var(--horizontal-padding) 0;
      margin-bottom: var(--turn-gap);
      contain: layout style;
    }
    .turn.has-pager { grid-template-columns: var(--avatar-width) minmax(0, 1fr); }
    .turn.card {
      padding: 8px var(--horizontal-padding);
      border: .5px solid var(--line);
      border-radius: 10px;
      background: color-mix(in srgb, var(--panel) 55%, transparent);
    }
    .portrait-lane {
      display: flex; flex-direction: column; align-self: stretch;
      width: 100%; min-width: 0; gap: 6px;
    }
    .avatar {
      display: flex;
      width: var(--avatar-width);
      height: var(--avatar-height);
      align-items: center;
      justify-content: center;
      border-radius: var(--avatar-radius);
      background: var(--avatar-background);
      color: var(--avatar-placeholder);
      overflow: hidden;
      user-select: none;
      -webkit-tap-highlight-color: transparent;
    }
    .avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .avatar-placeholder { width: 58%; height: 58%; fill: currentColor; }
    .pager {
      display: grid;
      grid-template-columns: 14px minmax(24px, 1fr) 14px;
      column-gap: 2px;
      align-items: center;
      width: var(--avatar-width);
      min-width: 0;
      height: 18px;
      color: var(--muted);
      text-shadow: none;
      white-space: nowrap;
      -webkit-text-size-adjust: none;
    }
    .pager button {
      min-width: 0; width: 14px; height: 18px; padding: 0;
      display: inline-flex; align-items: center; justify-content: center;
    }
    .pager button[data-action="opening-prev"]::before,
    .pager button[data-action="opening-next"]::before {
      content: ""; display: block; width: 6px; height: 6px;
      border-left: 1.35px solid currentColor; border-bottom: 1.35px solid currentColor;
    }
    .pager button[data-action="opening-prev"]::before { transform: translateX(1px) rotate(45deg); }
    .pager button[data-action="opening-next"]::before { transform: translateX(-1px) rotate(225deg); }
    .pager .pager-index {
      width: 100%; min-width: 0; height: 18px; padding: 0;
      font: 500 10px/16px sans-serif; letter-spacing: 0; text-align: center; white-space: nowrap;
    }
    .pager button:disabled { color: var(--soft); opacity: .42; }
    .icon { display: block; overflow: visible; pointer-events: none; }
    .turn-main { min-width: 0; }
    .turn-header {
      position: relative;
      min-height: 26px;
      margin-bottom: var(--reply-gap);
    }
    .name {
      width: 100%; padding-right: 60px; color: var(--text); font-size: var(--name-size);
      font-weight: 500; line-height: var(--name-line-height); overflow-wrap: anywhere;
      transition: padding-right 0ms linear 125ms;
    }
    .turn-header.toolbar-expanded .name { padding-right: 172px; transition-delay: 0ms; }
    .tools {
      position: absolute; right: 0; top: 0; width: 166px; height: 26px;
      display: flex; justify-content: flex-end; align-items: flex-start; color: var(--muted); text-shadow: none;
    }
    .tool-strip { display: flex; align-items: flex-start; justify-content: flex-end; gap: 2px; height: 26px; opacity: 1; transition: opacity 125ms cubic-bezier(.42,0,.58,1); }
    .tool-strip.swapping { opacity: 0; }
    .tool-slot { width: 26px; height: 26px; display: inline-flex; align-items: flex-start; justify-content: center; }
    .tool-slot:disabled { color: color-mix(in srgb, var(--soft) 58%, transparent); }
    .overflow-icon { width: 22px; height: 22px; }
    .filled-icon { display: block; width: 18px; height: 18px; object-fit: contain; pointer-events: none; }
    .pending-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); animation: pulse 1s ease-in-out infinite; }
    @keyframes pulse { 50% { opacity: .28; transform: scale(.78); } }
    .message-body { min-width: 0; }
    .roleplay-wrapper { white-space: pre-line; }
    .live-status {
      width: 100%; height: 25px; min-width: 0;
      display: flex; align-items: center; gap: 4px;
      color: var(--muted); text-align: left; text-shadow: none;
    }
    .live-status-indicator {
      width: 33px; height: 25px; flex: 0 0 33px;
      display: inline-flex; align-items: center; justify-content: center;
      color: var(--muted);
    }
    .live-status-operation-icon {
      width: 17px; height: 17px; display: block; overflow: visible;
      animation: live-status-dissolve-in 200ms 60ms linear both;
    }
    .thinking-mascot {
      position: relative; width: 27px; height: 24px; display: block;
      animation: live-status-dissolve-in 200ms 60ms linear both;
    }
    .thinking-mascot .mascot-sprite {
      position: absolute; left: 0; bottom: 0; width: 21px; height: 21px;
      object-fit: contain; image-rendering: pixelated; transform-origin: 50% 86%;
    }
    .thinking-mascot .mascot-half, .thinking-mascot .mascot-closed { opacity: 0; }
    .thinking-mascot.animated .mascot-sprite {
      animation-duration: 2700ms, 1050ms;
      animation-timing-function: steps(1,end), cubic-bezier(.4,0,.2,1);
      animation-iteration-count: infinite, infinite;
      animation-direction: normal, alternate;
      animation-delay: var(--blink-delay), var(--tilt-delay);
    }
    .thinking-mascot.animated .mascot-open { animation-name: mascot-open, mascot-tilt; }
    .thinking-mascot.animated .mascot-half { animation-name: mascot-half, mascot-tilt; }
    .thinking-mascot.animated .mascot-closed { animation-name: mascot-closed, mascot-tilt; }
    .thinking-mascot .idea-bulb {
      position: absolute; width: 8px; height: 8px; right: 3px; top: 1px;
      transform-origin: center; overflow: visible;
    }
    .thinking-mascot.animated .idea-bulb {
      animation: bulb-scale 560ms cubic-bezier(.4,0,.2,1) var(--bulb-delay) infinite alternate;
    }
    .thinking-mascot .bulb-ray { opacity: .72; }
    .thinking-mascot .bulb-core-ray { opacity: .676; }
    .thinking-mascot.animated .bulb-ray {
      animation: bulb-ray 420ms linear var(--spark-delay) infinite alternate;
    }
    .thinking-mascot.animated .bulb-core-ray {
      animation: bulb-core-ray 420ms linear var(--spark-delay) infinite alternate;
    }
    .thinking-bars {
      width: 27px; height: 24px; display: inline-flex; align-items: center; justify-content: center;
      gap: 2.5px; color: var(--accent); animation: live-status-dissolve-in 200ms 60ms linear both;
    }
    .thinking-bars > span {
      width: 3px; border-radius: 2px; background: currentColor; transform-origin: center bottom;
    }
    .thinking-bars > span:nth-child(1) { height: 9px; }
    .thinking-bars > span:nth-child(2) { height: 17px; }
    .thinking-bars > span:nth-child(3) { height: 12px; }
    .thinking-bars.animated > span { animation: thinking-bar-pulse 780ms ease-in-out infinite alternate; }
    .thinking-bars.animated > span:nth-child(2) { animation-delay: -520ms; }
    .thinking-bars.animated > span:nth-child(3) { animation-delay: -260ms; }
    @keyframes thinking-bar-pulse { from { transform: scaleY(.48); opacity: .48; } to { transform: scaleY(1); opacity: 1; } }
    .live-status-label {
      flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      font: 500 13px/25px sans-serif;
    }
    .live-status.running .live-status-label {
      color: transparent;
      background-color: color-mix(in srgb, var(--muted) 48%, transparent);
      background-image: linear-gradient(90deg,
        transparent 0%,
        var(--muted) 50%,
        transparent 100%);
      background-repeat: no-repeat; background-size: 56% 100%; background-position: -45% 0;
      -webkit-background-clip: text; background-clip: text;
      animation: live-status-shimmer 1400ms linear var(--shimmer-delay) infinite;
    }
    .live-status-chevron { width: 16px; height: 16px; flex: 0 0 16px; opacity: .7; }
    @keyframes live-status-dissolve-in { from { opacity: 0; } to { opacity: 1; } }
    @keyframes live-status-shimmer { from { background-position: -45% 0; } to { background-position: 145% 0; } }
    @keyframes mascot-tilt { from { transform: rotate(-10.5deg); } to { transform: rotate(-6.5deg); } }
    @keyframes mascot-open {
      0%,30.36% { opacity: 1; } 30.37%,40.74% { opacity: 0; }
      40.75%,70.36% { opacity: 1; } 70.37%,79.26% { opacity: 0; } 79.27%,100% { opacity: 1; }
    }
    @keyframes mascot-half {
      0%,30.36% { opacity: 0; } 30.37%,33.33% { opacity: 1; } 33.34%,37.77% { opacity: 0; }
      37.78%,40.74% { opacity: 1; } 40.75%,70.36% { opacity: 0; }
      70.37%,72.96% { opacity: 1; } 72.97%,76.66% { opacity: 0; }
      76.67%,79.26% { opacity: 1; } 79.27%,100% { opacity: 0; }
    }
    @keyframes mascot-closed {
      0%,33.33% { opacity: 0; } 33.34%,37.77% { opacity: 1; } 37.78%,72.96% { opacity: 0; }
      72.97%,76.66% { opacity: 1; } 76.67%,100% { opacity: 0; }
    }
    @keyframes bulb-scale { from { transform: scale(.84); } to { transform: scale(1.12); } }
    @keyframes bulb-ray { from { opacity: .22; } to { opacity: .95; } }
    @keyframes bulb-core-ray { from { opacity: .901; } to { opacity: .5725; } }
    .eleckoi-rich-replacement, .stream-block { display: contents; }
    .eleckoi-rich-replacement.eleckoi-rich-document { display: block; width: 100%; }
    .eleckoi-rich-document > template { display: none; }
    .eleckoi-rich-frame {
      display: block; width: 100%; height: 1px; margin: 0; padding: 0;
      border: 0; background: transparent; overflow: hidden;
    }
    .native-part > .content-part + .content-part { margin-top: 10px; }
    .native-part p { margin: 0 0 var(--paragraph-gap); }
    .native-part > .content-text-part > .stream-block:last-child > p:last-child { margin-bottom: 0; }
    .native-part h1, .native-part h2, .native-part h3, .native-part h4, .native-part h5, .native-part h6 {
      margin: calc(var(--paragraph-gap) * 1.5) 0 var(--paragraph-gap); line-height: 1.3;
    }
    .native-part h1 { font-size: 1.55em; } .native-part h2 { font-size: 1.35em; }
    .native-part h3 { font-size: 1.18em; }
    .native-part em { color: var(--italic-text); }
    .native-part u { color: var(--underline-text); text-decoration-color: currentColor; }
    .native-part .inline-quote { color: var(--quote-text); }
    .native-part blockquote { margin: var(--paragraph-gap) 0; padding-left: 10px; border-left: 2px solid var(--quote-text); color: var(--quote-text); }
    .native-part ul, .native-part ol { margin: var(--paragraph-gap) 0; padding-left: 1.55em; }
    .native-part pre {
      position: relative; margin: var(--paragraph-gap) 0; padding: 11px 12px; max-height: 420px;
      overflow: auto; border: .5px solid var(--code-border); border-radius: 6px;
      background: var(--code-background); color: var(--code-foreground); text-shadow: none;
      white-space: pre; overflow-wrap: normal; word-break: normal; font-size: .92em; line-height: var(--line-height);
    }
    .native-part pre.wrap { white-space: pre-wrap; overflow-wrap: anywhere; }
    .native-part pre.show-all { max-height: none; }
    .native-part code { padding: .08em .28em; border-radius: 4px; background: color-mix(in srgb, var(--code-background) 72%, transparent); color: var(--inline-code-text); font-family: monospace; text-shadow: none; }
    .native-part pre code { padding: 0; background: transparent; color: inherit; }
    .native-part .code-workbench { padding: 0; border-radius: 8px; }
    .code-header { display: flex; min-height: 28px; align-items: center; padding: 5px 6px 5px 12px; border-bottom: 1px solid var(--code-border); background: var(--code-header); color: color-mix(in srgb, var(--code-foreground) 66%, transparent); font: 500 .76em/1 sans-serif; }
    .code-workbench code { display: block; padding: 10px 12px; }
    .native-part a { color: var(--underline-text); text-decoration-color: currentColor; text-underline-offset: 2px; }
    .native-part hr { border: 0; border-top: 1px solid var(--line); margin: 12px 0; }
    .native-part details { margin: var(--paragraph-gap) 0; }
    .native-part details > summary { cursor: pointer; user-select: none; -webkit-user-select: none; }
    .native-part details[open] > summary { margin-bottom: var(--paragraph-gap); }
    .native-part .collapsible-content > :first-child { margin-top: 0; }
    .native-part .collapsible-content > :last-child { margin-bottom: 0; }
    .reasoning { margin-top: 8px; color: var(--muted); text-shadow: none; }
    .reasoning summary { cursor: pointer; user-select: none; }
    .reasoning-content { margin-top: 6px; white-space: pre-wrap; }
    .story-images { display: grid; gap: 4px; width: 100%; }
    .story-images.single { display: flex; justify-content: center; }
    .story-image-frame {
      position: relative; min-width: 0; overflow: hidden; border-radius: 12px;
      background: rgba(0,0,0,.2); user-select: none; -webkit-user-select: none;
      -webkit-touch-callout: none; touch-action: pan-y;
    }
    .story-image { display: block; width: 100%; height: 100%; object-fit: contain; background: rgba(0,0,0,.2); }
    .image-state { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; padding: 12px; border: 1px dashed var(--line); border-radius: 12px; color: var(--muted); text-align: center; text-shadow: none; }
    .image-frame-badge { position: absolute; left: 9px; top: 9px; padding: 4px 8px; border-radius: 10px; color: white; background: rgba(0,0,0,.58); font: 500 11px/1 sans-serif; text-shadow: none; pointer-events: none; }
    .image-menu {
      position: fixed; z-index: 90; min-width: 136px; padding: 6px;
      border: .5px solid color-mix(in srgb, var(--body-text) 18%, transparent);
      border-radius: 12px; color: var(--body-text); background: var(--panel); text-shadow: none;
      box-shadow: 0 8px 28px rgba(0,0,0,.28);
    }
    .image-menu[hidden] { display: none; }
    .image-menu button { display: flex; width: 100%; min-height: 42px; align-items: center; padding: 0 14px; border-radius: 8px; font-size: 15px; text-align: left; }
    .image-menu button:active { background: color-mix(in srgb, var(--body-text) 9%, transparent); }
    #empty { display: none; padding: 40px var(--horizontal-padding); color: var(--muted); text-align: center; text-shadow: none; }
    @media (prefers-reduced-motion: reduce) {
      .tool-strip { transition: none; }
      .pending-dot, .live-status-label, .thinking-mascot, .thinking-mascot *, .thinking-bars, .thinking-bars *,
      .live-status-operation-icon { animation: none !important; }
    }
  """
