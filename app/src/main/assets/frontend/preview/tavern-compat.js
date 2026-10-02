(function installTavernCompatibility(global) {
  'use strict';
  if (global.ElecKoi?.compatibility?.version === '0.1.0') return;
  const base = global.ElecKoi;
  if (!base) throw new Error('ElecKoi SDK must load before tavern-compat.js');
  const pluginId = global.__ElecKoiPluginId || 'frontend';
  const call = (method, params = {}) => base.call(method, { pluginId, ...params });
  const clone = value => JSON.parse(JSON.stringify(value));
  const listeners = new Map();
  const cleanups = new Set();
  const injections = new Map();
  const macros = new Map();
  const commands = new Map();
  const variableCommits = new WeakMap();
  const activeGenerations = new Set();
  let state = null;
  let writeQueue = Promise.resolve();
  const writeErrors = [];
  const extensionSettings = {};
  let refreshQueue = Promise.resolve();

  function report(error) {
    console.error('[ElecKoi plugin]', error);
    global.dispatchEvent(new CustomEvent('eleckoi:plugin-error', { detail: { pluginId, error } }));
  }
  function requireState() {
    if (!state) throw new Error('Context is not ready; await ElecKoi.ready() before using synchronous Tavern APIs');
    return state;
  }
  function captured() {
    const s = requireState();
    return { conversationId: s.conversationId, characterId: s.characterId, presetId: s.presetId };
  }
  function enqueue(task, rollback) {
    const result = writeQueue.then(task, task);
    writeQueue = result;
    result.catch(error => {
      if (rollback) rollback();
      writeErrors.push(error);
      report(error);
    });
    return result;
  }
  async function flush() {
    try { await writeQueue; } catch (error) { if (!writeErrors.includes(error)) writeErrors.push(error); }
    if (writeErrors.length) throw new AggregateError(writeErrors.splice(0), 'Plugin persistence failed');
  }

  function eventOn(event, fn, position = 'last', once = false) {
    if (typeof fn !== 'function') throw new TypeError('Event listener must be a function');
    const entries = listeners.get(event) || [];
    const existing = entries.find(entry => entry.fn === fn);
    if (existing) return existing.handle;
    const entry = { fn, once, handle: { stop: () => eventRemoveListener(event, fn) } };
    if (position === 'first') entries.unshift(entry); else entries.push(entry);
    listeners.set(event, entries);
    return entry.handle;
  }
  function eventRemoveListener(event, fn) {
    const entries = listeners.get(event);
    if (!entries) return;
    const remaining = entries.filter(entry => entry.fn !== fn);
    if (remaining.length) listeners.set(event, remaining); else listeners.delete(event);
  }
  async function eventEmit(event, ...args) {
    for (const entry of [...(listeners.get(event) || [])]) {
      if (entry.once) entry.handle.stop();
      await entry.fn(...args);
    }
  }
  function moveListener(event, fn, first) {
    const entries = listeners.get(event) || [];
    const entry = entries.find(value => value.fn === fn);
    if (!entry) return eventOn(event, fn, first ? 'first' : 'last');
    entries.splice(entries.indexOf(entry), 1);
    if (first) entries.unshift(entry); else entries.push(entry);
    return entry.handle;
  }

  const tavern_events = Object.freeze(Object.fromEntries([
    'APP_READY', 'CHAT_CHANGED', 'MESSAGE_SENT', 'MESSAGE_RECEIVED', 'MESSAGE_UPDATED', 'MESSAGE_EDITED', 'MESSAGE_DELETED',
    'GENERATION_STARTED', 'GENERATION_ENDED', 'GENERATION_STOPPED', 'STREAM_TOKEN_RECEIVED',
  ].map(name => [name, name.toLowerCase()])));
  const iframe_events = Object.freeze({ GENERATION_REQUESTED: 'js_generation_requested', GENERATION_STARTED: 'js_generation_started', GENERATION_ENDED: 'js_generation_ended',
    STREAM_TOKEN_RECEIVED_FULLY: 'js_stream_token_received_fully', STREAM_TOKEN_RECEIVED_INCREMENTALLY: 'js_stream_token_received_incrementally',
    REASONING_TOKEN_RECEIVED_FULLY: 'js_reasoning_token_received_fully', REASONING_TOKEN_RECEIVED_INCREMENTALLY: 'js_reasoning_token_received_incrementally' });

  function indexOf(value, messages = requireState().messages) {
    if (typeof value === 'string' && !/^-?\d+$/.test(value)) {
      const index = messages.findIndex(message => message.id === value);
      if (index < 0) throw new RangeError(`Message does not exist: ${value}`);
      return index;
    }
    const number = Number(value);
    const index = number < 0 ? messages.length + number : number;
    if (!Number.isInteger(index) || index < 0 || index >= messages.length) throw new RangeError(`Message index out of range: ${value}`);
    return index;
  }
  function indices(range = '0-{{lastMessageId}}', messages = requireState().messages) {
    if (!messages.length && range === '0-{{lastMessageId}}') return [];
    if (Array.isArray(range)) return [...new Set(range.map(value => indexOf(value, messages)))];
    const text = String(range).replace('{{lastMessageId}}', String(messages.length - 1));
    const match = /^(-?\d+)-(-?\d+)$/.exec(text);
    if (!match) return [indexOf(text, messages)];
    const start = indexOf(match[1], messages), end = indexOf(match[2], messages);
    return Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index);
  }
  function tavernMessage(message, index) {
    const extra = message.metadata || {};
    return { ...clone(extra), message_id: index, name: message.role === 'user' ? state.userName : state.characterName,
      role: message.role, is_user: message.role === 'user', is_system: message.role === 'system',
      message: message.content, mes: message.content, data: clone(extra.data || {}), extra: clone(extra.extra || {}),
      native_id: message.id, swipe_id: message.swipe_id || 0, swipes: clone(message.swipes || [message.content]), reasoning: message.reasoning || '' };
  }
  const messageKeys = new Set(['message_id', 'native_id', 'role', 'name', 'is_user', 'is_system', 'message', 'mes', 'swipe_id', 'swipes', 'reasoning']);
  function messageMetadata(value) {
    return Object.fromEntries(Object.entries(value).filter(([key]) => !messageKeys.has(key)));
  }
  async function getChatMessages(range, options = {}) {
    await readyPromise;
    return indices(range).map(index => tavernMessage(state.messages[index], index))
      .filter(message => !options.role || options.role === 'all' || message.role === options.role)
      .filter(message => !options.hide_state || options.hide_state === 'all' || (options.hide_state === 'hidden' ? message.is_hidden : !message.is_hidden));
  }
  async function setChatMessages(updates, options = {}) {
    await readyPromise;
    const scope = captured();
    const snapshot = state.messages;
    const messages = [];
    for (const value of updates) {
      const index = indexOf(value.native_id ?? value.message_id, snapshot);
      const original = snapshot[index];
      if (value.role && value.role !== original.role) throw new Error('Changing the role of an existing message requires delete/create');
      let expectedContent = original.content;
      if (value.swipe_id !== undefined || value.swipes) {
        const { swipe_id, swipes } = value;
        const result = await call('messages.swipes', { ...scope, id: original.id, swipe_id, swipes });
        expectedContent = result.swipes[result.swipe_id];
      }
      const metadata = { ...(original.metadata || {}), ...messageMetadata(value) };
      const content = value.message ?? value.mes;
      messages.push({ id: original.id, metadata, ...(content === undefined ? {} : { content, expectedContent }) });
    }
    await flush();
    const updated = await call('messages.update', { ...scope, messages });
    if (state.conversationId === scope.conversationId) { state.messages = updated; updateContextChat(); }
  }
  async function createChatMessages(messages, options = {}) {
    await readyPromise;
    const scope = captured();
    let index = options.insert_before === undefined ? state.messages.length : indexOf(options.insert_before);
    const updated = await call('messages.insert', { ...scope, index, messages: messages.map(value => ({
      role: value.role || (value.is_user ? 'user' : value.is_system ? 'system' : 'assistant'), content: value.message ?? value.mes ?? '', metadata: messageMetadata(value) })) });
    if (state.conversationId === scope.conversationId) { state.messages = updated; updateContextChat(); }
  }
  async function deleteChatMessages(range) {
    await readyPromise;
    const scope = captured(), ids = indices(range).map(index => state.messages[index].id);
    const updated = await call('messages.delete', { ...scope, ids });
    if (state.conversationId === scope.conversationId) { state.messages = updated; updateContextChat(); }
  }

  function variableLocation(options = {}) {
    const s = requireState(), type = options.type || options.scope || 'chat';
    const scope = type === 'script' || type === 'extension' ? type : type === 'plugin' ? 'plugin' : type;
    if (!['chat', 'message', 'character', 'preset', 'global', 'script', 'extension', 'plugin'].includes(scope)) throw new Error(`Unknown variable scope: ${scope}`);
    const messageId = scope === 'message' ? s.messages[indexOf(options.message_id ?? -1)].id : undefined;
    const bucket = scope === 'message' ? (s.variables.message[messageId] ||= {}) : (s.variables[scope] ||= {});
    return { bucket, scope, messageId, context: captured() };
  }
  function getVariables(options = {}) { return clone(variableLocation(options).bucket); }
  function replaceLocation(value, location) {
    const record = variableCommits.get(location.bucket) || { committed: clone(location.bucket), revision: 0 };
    variableCommits.set(location.bucket, record);
    const revision = ++record.revision;
    Object.keys(location.bucket).forEach(key => delete location.bucket[key]); Object.assign(location.bucket, clone(value));
    const payload = clone(location.bucket);
    enqueue(async () => {
      await call('variables.writeScope', { ...location.context, scope: location.scope, messageId: location.messageId, value: payload });
      record.committed = payload;
    }, () => {
      if (record.revision !== revision) return;
      Object.keys(location.bucket).forEach(key => delete location.bucket[key]); Object.assign(location.bucket, record.committed);
    });
    return undefined;
  }
  function replaceVariables(value, options = {}) { return replaceLocation(value, variableLocation(options)); }
  function updateVariablesWith(updater, options = {}) {
    const location = variableLocation(options), updated = updater(clone(location.bucket));
    if (updated?.then) return updated.then(value => { replaceLocation(value, location); return flush().then(() => value); });
    replaceLocation(updated, location); return clone(updated);
  }
  function insertOrAssignVariables(value, options) { const updated = { ...getVariables(options), ...value }; replaceVariables(updated, options); return updated; }
  function insertVariables(value, options) { const updated = { ...value, ...getVariables(options) }; replaceVariables(updated, options); return updated; }
  function deleteVariable(path, options) {
    const value = getVariables(options), parts = String(path).split('.'), last = parts.pop();
    let object = value; for (const part of parts) { object = object[part]; if (!object) return false; }
    const exists = Object.hasOwn(object, last); delete object[last]; replaceVariables(value, options); return exists;
  }

  async function persistInjections() {
    const entries = [...injections.values()];
    const allowed = [];
    for (const entry of entries) {
      const { filter, ...plain } = entry;
      if (!filter || await filter()) allowed.push(plain);
    }
    await call('prompts.set', { entries: allowed });
  }
  function injectPrompts(entries, options = {}) {
    const scope = captured();
    for (const entry of entries) {
      if (entry.depth !== undefined && (!Number.isInteger(entry.depth) || entry.depth < 0)) throw new RangeError('Prompt depth must be a non-negative integer');
      injections.set(entry.id, { ...scope, ...entry, once: !!options.once });
    }
    enqueue(persistInjections);
    return { uninject: () => uninjectPrompts(entries.map(entry => entry.id)) };
  }
  function uninjectPrompts(ids) { ids.forEach(id => injections.delete(id)); enqueue(persistInjections); }
  function setExtensionPrompt(id, content, position = 1, depth = 0, scan = false, role = 0) {
    const roles = ['system', 'user', 'assistant'];
    const anchors = { '-1': 'beforeLatestUserInput', 0: 'afterToolContext', 1: 'beforeLatestUserInput', 2: 'beforeToolContext' };
    if (!(position in anchors)) throw new Error(`Extension prompt position is not supported: ${position}`);
    return injectPrompts([{ id, content, anchor: anchors[position], position: position === -1 ? 'none' : 'in_chat',
      role: typeof role === 'string' ? role : roles[role], ...(position === 1 ? { depth } : {}), should_scan: scan }]);
  }

  async function generateRequest(options = {}, raw) {
    await readyPromise; await flush();
    const id = options.generation_id || `generation-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    activeGenerations.add(id);
    let full = '', fullReasoning = '';
    const stop = base.events.on('generation.delta', packet => {
      if (packet.id !== id) return;
      full += packet.delta || ''; fullReasoning += packet.reasoning || '';
      if (packet.delta) {
        eventEmit(iframe_events.STREAM_TOKEN_RECEIVED_INCREMENTALLY, packet.delta, id).catch(report);
        eventEmit(iframe_events.STREAM_TOKEN_RECEIVED_FULLY, full, id).catch(report);
      }
      if (packet.reasoning) {
        eventEmit(iframe_events.REASONING_TOKEN_RECEIVED_INCREMENTALLY, packet.reasoning, id).catch(report);
        eventEmit(iframe_events.REASONING_TOKEN_RECEIVED_FULLY, fullReasoning, id).catch(report);
      }
    });
    try {
      await eventEmit(iframe_events.GENERATION_REQUESTED, id);
      await eventEmit(iframe_events.GENERATION_STARTED, id);
      const custom = options.custom_api || {};
      if (custom.apiurl || custom.key) throw new Error('custom_api endpoint override is not implemented; select a host configId or use ElecKoi.net.fetch');
      const messages = options.ordered_prompts || options.messages;
      const result = await call('generation.invoke', { ...captured(), id, raw, prompt: options.user_input ?? options.prompt ?? '',
        ...(messages ? { messages: messages.map(message => ({ role: message.role, content: message.content })) } : {}),
        purpose: options.purpose || 'plugin', stream: options.should_stream ?? options.stream ?? false,
        configId: options.configId || custom.configId, model: options.model || custom.model,
        responseFormat: options.responseFormat, parameters: options.parameters || {} });
      await eventEmit(iframe_events.GENERATION_ENDED, result.content, id);
      return options.return_reasoning ? { text: result.content, reasoning: result.reasoning } : result.content;
    } finally { stop(); activeGenerations.delete(id); }
  }

  async function getWorldbook(name) {
    const document = await call('worldbooks.get', { name });
    return clone(document.entries || []);
  }
  async function replaceWorldbook(name, entries) {
    const previous = await call('worldbooks.get', { name });
    await call('worldbooks.put', { name, book: { ...previous, entries } });
  }
  async function createWorldbook(name, entries = []) { await call('worldbooks.put', { name, book: { name, entries } }); }
  async function updateWorldbookWith(name, updater) { const entries = await updater(await getWorldbook(name)); await replaceWorldbook(name, entries); return entries; }

  function substituteMacros(text) {
    const s = requireState();
    return String(text).replace(/\{\{(user|char|lastMessageId|[^{}:]+)\}\}/g, (original, name) => {
      if (name === 'user') return s.userName;
      if (name === 'char') return s.characterName;
      if (name === 'lastMessageId') return String(s.messages.length - 1);
      return macros.has(name) ? String(typeof macros.get(name) === 'function' ? macros.get(name)() : macros.get(name)) : original;
    });
  }
  function registerMacro(name, value) {
    macros.set(name, value);
    enqueue(() => call('macros.register', { name, value: typeof value === 'function' ? String(value()) : String(value) }));
    return () => { macros.delete(name); enqueue(() => call('macros.unregister', { name })); };
  }
  function splitCommands(text) {
    const result = []; let current = '', quote = '', escape = false;
    for (const char of text) {
      if (escape) { current += char; escape = false; continue; }
      if (char === '\\') { escape = true; current += char; continue; }
      if (quote) { current += char; if (char === quote) quote = ''; continue; }
      if (char === '"' || char === "'") { quote = char; current += char; continue; }
      if (char === '|') { result.push(current.trim()); current = ''; } else current += char;
    }
    if (quote) throw new Error('Unterminated quote in Slash command');
    if (current.trim()) result.push(current.trim());
    return result;
  }
  async function triggerSlash(text) {
    await readyPromise;
    let pipe = '';
    for (const source of splitCommands(String(text))) {
      const match = /^\/(\S+)(?:\s+([\s\S]*))?$/.exec(source);
      if (!match) throw new Error(`Invalid Slash command: ${source}`);
      const name = match[1], body = substituteMacros(match[2] || '').replace(/\{\{pipe\}\}/g, pipe);
      const custom = commands.get(name);
      if (custom) { pipe = String(await custom({}, body)); continue; }
      switch (name) {
        case 'send': await base.input.set(body); pipe = body; break;
        case 'trigger': await flush(); await base.input.send(); break;
        case 'gen': case 'genraw': pipe = await generateRequest({ user_input: body }, name === 'genraw'); break;
        case 'echo': pipe = body; break;
        case 'setvar': { const m = /^key=(\S+)\s+([\s\S]*)$/.exec(body); if (!m) throw new Error('/setvar requires key=NAME VALUE'); insertOrAssignVariables({ [m[1]]: m[2] }); break; }
        case 'getvar': pipe = String(getVariables()[body] ?? ''); break;
        default: throw new Error(`Unsupported Slash command: /${name}; register a command callback to implement it`);
      }
    }
    return pipe;
  }

  const context = {
    chat: [], chatMetadata: {}, extensionSettings, eventSource: {
      on: (event, fn) => eventOn(event, fn), once: (event, fn) => eventOn(event, fn, 'last', true),
      removeListener: eventRemoveListener, emit: eventEmit, emitAndWait: eventEmit,
      makeFirst: (event, fn) => moveListener(event, fn, true), makeLast: (event, fn) => moveListener(event, fn, false),
    }, eventTypes: tavern_events,
    getCurrentChatId: () => requireState().conversationId,
    getRequestHeaders: () => ({ 'Content-Type': 'application/json' }),
    saveChat: async () => {
      const s = requireState(), scope = captured();
      const updates = context.chat.map((value, index) => {
        const original = s.messages[indexOf(value.native_id ?? index)];
        return { id: original.id, content: value.mes, expectedContent: original.content, metadata: messageMetadata(value) };
      }).filter(value => value.content !== s.messages.find(message => message.id === value.id).content ||
        JSON.stringify(value.metadata) !== JSON.stringify(s.messages.find(message => message.id === value.id).metadata || {}));
      if (updates.length) await call('messages.update', { ...scope, messages: updates });
      await call('messages.metadata', { ...scope, value: context.chatMetadata });
      await refresh();
    },
    saveMetadata: () => call('messages.metadata', { ...captured(), value: clone(context.chatMetadata) }),
    saveSettingsDebounced: () => { const value = clone(extensionSettings); enqueue(() => call('settings.set', { value })); },
    setExtensionPrompt, substituteParams: substituteMacros, substituteParamsExtended: substituteMacros,
    generateQuietPrompt: (prompt, options = {}) => generateRequest({ ...options, user_input: prompt }, false),
    executeSlashCommands: async command => ({ pipe: await triggerSlash(command) }),
    registerMacro, unregisterMacro: name => { macros.delete(name); enqueue(() => call('macros.unregister', { name })); },
    getTokenCount: () => { throw new Error('Exact provider token counting is not implemented'); },
  };
  function updateContextChat() {
    context.chat = state.messages.map((message, index) => tavernMessage(message, index));
    context.chatMetadata = state.metadata || {};
    context.characterId = state.characterId; context.name1 = state.userName; context.name2 = state.characterName;
  }
  async function refresh(conversationId) {
    await flush();
    const next = await call('plugins.bootstrap', conversationId ? { conversationId } : {});
    const previous = state;
    state = next;
    Object.keys(extensionSettings).forEach(key => delete extensionSettings[key]); Object.assign(extensionSettings, next.settings);
    updateContextChat();
    if (previous && previous.conversationId !== next.conversationId) {
      injections.clear(); await persistInjections(); await eventEmit(tavern_events.CHAT_CHANGED, next.conversationId);
    }
    return previous;
  }
  const readyPromise = refresh();
  readyPromise.catch(report);
  function ready() { return readyPromise; }
  ready.then = readyPromise.then.bind(readyPromise);
  ready.catch = readyPromise.catch.bind(readyPromise);

  const helpers = {
    getChatMessages, setChatMessages, createChatMessages, deleteChatMessages,
    getLastMessageId: () => requireState().messages.length - 1,
    getCurrentMessageId: () => { const id = global.__ElecKoiCurrentMessageId; return id ? indexOf(id) : requireState().messages.length - 1; },
    getVariables, replaceVariables, updateVariablesWith, insertOrAssignVariables, insertVariables, deleteVariable,
    eventOn, eventOnce: (event, fn) => eventOn(event, fn, 'last', true),
    eventMakeFirst: (event, fn) => moveListener(event, fn, true), eventMakeLast: (event, fn) => moveListener(event, fn, false),
    eventRemoveListener, eventClearEvent: event => listeners.delete(event), eventClearAll: () => listeners.clear(), eventEmit,
    generate: options => generateRequest(options, false), generateRaw: options => generateRequest(options, true),
    getModelList: async customApi => {
      if (customApi?.apiurl) {
        const response = await call('network.request', { url: customApi.apiurl.replace(/\/$/, '') + '/models', headers: customApi.key ? { Authorization: `Bearer ${customApi.key}` } : {} });
        if (!response.ok) throw new Error(`Model list HTTP ${response.status}: ${response.body}`);
        return [...new Set(JSON.parse(response.body).data.map(value => String(value.id || value.name)))].sort();
      }
      const configs = (await base.chat.getModels()).items || [];
      return [...new Set(configs.flatMap(config => config.models?.map(model => model.id) || [config.defaultModel]))].filter(Boolean).sort();
    },
    stopGenerationById: id => call('generation.cancel', { id }),
    stopAllGeneration: async () => Promise.all([...activeGenerations].map(id => call('generation.cancel', { id }))),
    injectPrompts, uninjectPrompts,
    getWorldbookNames: () => call('worldbooks.list'), getWorldbook, replaceWorldbook, updateWorldbookWith, createWorldbook,
    deleteWorldbook: name => call('worldbooks.delete', { name }),
    createWorldbookEntries: (name, entries) => updateWorldbookWith(name, old => [...old, ...entries]),
    deleteWorldbookEntries: (name, predicate) => updateWorldbookWith(name, old => old.filter(entry => !predicate(entry))),
    getChatWorldbookName: async () => (await call('worldbooks.bindings', { ...captured(), scope: 'chat' }))[0] || null,
    rebindChatWorldbook: name => call('worldbooks.bind', { ...captured(), scope: 'chat', names: name ? [name] : [] }),
    getGlobalWorldbookNames: () => call('worldbooks.bindings', { scope: 'global' }),
    rebindGlobalWorldbooks: names => call('worldbooks.bind', { scope: 'global', names }),
    getCharWorldbookNames: async () => ({ primary: `character:${requireState().characterId}`, additional: await call('worldbooks.bindings', { ...captured(), scope: 'character' }) }),
    rebindCharWorldbooks: value => call('worldbooks.bind', { ...captured(), scope: 'character', names: value.additional || [] }),
    getCharData: options => call('characters.read', { ...captured(), ...(options || {}) }),
    getCharAvatarPath: async options => { const value = await call('characters.read', { ...captured(), ...(options || {}) }); return value.avatar; },
    getPresetNames: async () => (await call('presets.list')).map(item => item.name),
    getPreset: id => call('presets.get', { id: id || requireState().presetId }),
    loadPreset: id => call('presets.select', { id }),
    replacePreset: (id, preset) => call('presets.update', { id, preset }),
    getTavernRegexes: async () => { const value = await call('regex.get', captured()); return [...value.global_rules, ...value.prompt_preset_rules, ...value.character_rules]; },
    replaceTavernRegexes: async rules => { const value = await call('regex.get', captured()); return call('regex.set', { ...captured(), rules: { ...value, character_rules: rules } }); },
    formatAsTavernRegexedString: async (text, source = 'ai_output', destination = 'display') => {
      const rules = await helpers.getTavernRegexes();
      for (const rule of rules.sort((a, b) => a.order - b.order)) {
        if (!rule.enabled || (destination === 'display' && rule.prompt_only) || (destination === 'prompt' && rule.display_only)) continue;
        const target = { ai_output: 'AiOutput', user_input: 'UserInput', reasoning: 'Reasoning', slash_command: 'SlashCommand', world_info: 'SettingContent' }[source];
        if (rule.targets?.length && !rule.targets.includes(target)) continue;
        const pattern = /^\/([\s\S]*)\/([a-z]*)$/.exec(rule.pattern);
        text = text.replace(pattern ? new RegExp(pattern[1], pattern[2]) : new RegExp(rule.pattern, 'g'), rule.replacement);
      }
      return text;
    },
    substituteMacros, triggerSlash, triggerSlashWithResult: triggerSlash,
    registerSlashCommand: (name, callback) => { commands.set(name, callback); return { stop: () => commands.delete(name) }; },
    getScriptId: () => pluginId,
    appendInexistentScriptButtons: buttons => buttons.forEach(button => enqueue(() => call('ui.register', { descriptor: { id: button.name, label: button.name, kind: 'script-button', html: button.html || '' } }))),
    getButtonEvent: name => `plugin:${pluginId}:button:${name}`,
    getScriptButtons: () => call('ui.list'),
    updateAudio: settings => base.audio.setSettings(settings),
    getAudioSettings: () => base.audio.getSettings(), setAudioSettings: value => base.audio.setSettings(value),
    getAudioPlaylist: channel => base.audio.getPlaylist(channel), replaceAudioPlaylist: (channel, items) => base.audio.setPlaylist(channel, items),
    appendAudioList: (channel, items) => base.audio.appendPlaylist(channel, items),
  };
  helpers.getLorebookNames = helpers.getWorldbookNames;
  helpers.getLorebookEntries = getWorldbook;
  helpers.setLorebookEntries = replaceWorldbook;

  function nativeEvent(name, action) {
    const stop = base.events.on(name, payload => {
      const handle = async () => {
        await readyPromise; const previous = await refresh(); await action(payload, previous);
      };
      refreshQueue = refreshQueue.then(handle, handle);
      refreshQueue.catch(report);
    });
    cleanups.add(stop);
  }
  nativeEvent('messages.changed', async (payload, previous) => {
    if (payload.conversationId && payload.conversationId !== state.conversationId) return;
    if (previous.conversationId !== state.conversationId) return;
    const before = new Map(previous.messages.map(message => [message.id, message]));
    for (let index = 0; index < state.messages.length; index++) {
      const message = state.messages[index], old = before.get(message.id);
      if (!old && message.role === 'user') await eventEmit(tavern_events.MESSAGE_SENT, index);
      if (old && message.content !== old.content && !message.pending) await eventEmit(tavern_events.MESSAGE_EDITED, index);
    }
    if (previous.messages.some(message => !state.messages.some(current => current.id === message.id))) await eventEmit(tavern_events.MESSAGE_DELETED, state.messages.length);
    await eventEmit(tavern_events.MESSAGE_UPDATED, payload);
  });
  nativeEvent('agent.run.finished', async (payload) => {
    const messageId = payload.message?.id || payload.messageId;
    const conversationId = payload.conversationId || state.conversationId;
    const index = conversationId === state.conversationId ? (messageId ? state.messages.findIndex(message => message.id === messageId)
      : state.messages.findLastIndex(message => message.role === 'assistant' && !message.pending)) : -1;
    if (index >= 0) await eventEmit(tavern_events.MESSAGE_RECEIVED, index);
    if (messageId || index >= 0) await eventEmit('message.committed', { messageId: messageId || state.messages[index].id, conversationId });
    await eventEmit(tavern_events.GENERATION_ENDED, payload);
  });
  nativeEvent('agent.state.changed', async payload => {
    if (payload.state === 'starting') await eventEmit(tavern_events.GENERATION_STARTED, payload);
    if (payload.detail === 'stopped') await eventEmit(tavern_events.GENERATION_STOPPED, payload);
  });
  nativeEvent('agent.run.failed', payload => eventEmit(tavern_events.GENERATION_STOPPED, payload));
  nativeEvent('chat.changed', () => Promise.resolve());
  cleanups.add(base.events.on('agent.output.delta', payload => { eventEmit(tavern_events.STREAM_TOKEN_RECEIVED, payload).catch(report); }));
  cleanups.add(base.events.on('plugin.event', payload => {
    if (payload.pluginId === pluginId) eventEmit(payload.event, payload.payload).catch(report);
  }));

  async function beforeGeneration(payload) {
    try {
      await readyPromise; await refresh(payload.conversationId);
      await eventEmit('generation.before', payload);
      for (const [name, value] of macros) if (typeof value === 'function') await call('macros.register', { name, value: String(value()) });
      await persistInjections(); await flush();
      await call('plugins.hookResult', { token: payload.token });
      for (const [id, injection] of injections) if (injection.once) injections.delete(id);
    } catch (error) {
      report(error); await call('plugins.hookResult', { token: payload.token, error: String(error) });
    } finally {
      await refresh();
    }
  }
  global.__ElecKoiBeforeGeneration = payload => {
    refreshQueue = refreshQueue.then(() => beforeGeneration(payload), () => beforeGeneration(payload));
    refreshQueue.catch(report);
    return refreshQueue;
  };
  global.__ElecKoiFlush = async token => {
    try { await flush(); await call('plugins.hookResult', { token }); }
    catch (error) { report(error); await call('plugins.hookResult', { token, error: String(error) }); }
  };

  async function invokeModel(options = {}) {
    await readyPromise; await flush();
    const id = options.id || `model-${Date.now()}-${Math.random()}`;
    activeGenerations.add(id);
    try { return await call('generation.invoke', { ...captured(), ...options, id }); }
    finally { activeGenerations.delete(id); }
  }
  async function hostFetch(url, options = {}) {
    const { signal, ...request } = options;
    if (signal?.aborted) throw signal.reason || new Error('Network request aborted');
    const id = request.id || `network-${Date.now()}-${Math.random()}`;
    const abort = () => call('network.cancel', { id }).catch(report);
    signal?.addEventListener('abort', abort, { once: true });
    try {
      const result = await call('network.request', { url, ...request, id });
      return { ...result, text: async () => result.body, json: async () => JSON.parse(result.body) };
    } finally { signal?.removeEventListener('abort', abort); }
  }
  const isNativeEvent = event => event !== 'generation.before' && (/^(agent\.|generation\.|audio\.)/.test(event) || ['messages.changed', 'chat.changed', 'plugin.event'].includes(event));

  global.ElecKoi = Object.freeze({ ...base, ready, flush,
    compatibility: Object.freeze({ version: '0.1.0', target: 'TavernHelper 4.11.2 / SillyTavern 1.19.0', helpers: Object.keys(helpers) }),
    plugins: Object.freeze({ list: () => call('plugins.list'), install: (id, manifest) => call('plugins.install', { id, manifest }),
      openManager: () => call('plugins.openManager'), remove: id => call('plugins.remove', { id }), setEnabled: (id, enabled) => call('plugins.setEnabled', { id, enabled }) }),
    storage: Object.freeze({ kv: { get: key => call('storage.get', { key }), set: (key, value) => call('storage.set', { key, value }),
      delete: key => call('storage.delete', { key }), list: () => call('storage.list') }, sqlite: { open: async database => ({
        execute: async (sql, params = []) => (await call('storage.sql', { database, statements: [{ sql, params }] }))[0],
        query: async (sql, params = []) => (await call('storage.sql', { database, statements: [{ sql, params }] }))[0].rows,
        transaction: statements => call('storage.transaction', { database, statements }),
      }) } }),
    settings: { get: () => call('settings.get'), set: value => call('settings.set', { value }) },
    files: { saveText: options => call('files.saveText', options) },
    generation: { invoke: invokeModel,
      invokeRaw: options => invokeModel({ ...options, raw: true }),
      start: async options => { await readyPromise; await flush(); return call('generation.start', { ...captured(), ...options }); },
      get: id => call('generation.get', { id }), cancel: id => call('generation.cancel', { id }) },
    prompt: { inject: injectPrompts, remove: uninjectPrompts, preview: () => clone([...injections.values()].map(({ filter, ...entry }) => entry)),
      beforeGeneration: listener => eventOn('generation.before', listener) },
    net: { fetch: hostFetch, cancel: id => call('network.cancel', { id }) },
    variables: { ...base.variables,
      readScope: options => call('variables.readScope', { ...captured(), ...options }),
      writeScope: (value, options) => call('variables.writeScope', { ...captured(), ...options, value }) },
    worldbooks: { list: () => call('worldbooks.list'), get: name => call('worldbooks.get', { name }),
      put: (name, book) => call('worldbooks.put', { name, book }), delete: name => call('worldbooks.delete', { name }),
      bind: (scope, names, options) => call('worldbooks.bind', { ...captured(), ...options, scope, names }),
      bindings: (scope, options) => call('worldbooks.bindings', { ...captured(), ...options, scope }) },
    characters: { list: () => call('characters.list'), read: options => call('characters.read', { ...captured(), ...options }),
      write: (character, options) => call('characters.write', { ...captured(), ...options, character }) },
    personas: { get: () => call('personas.get'), set: persona => call('personas.set', { persona }) },
    presets: { list: () => call('presets.list'), get: id => call('presets.get', { id: id || requireState().presetId }),
      select: id => call('presets.select', { id }), update: (id, preset) => call('presets.update', { id, preset }) },
    regex: { get: options => call('regex.get', { ...captured(), ...options }),
      set: (rules, options) => call('regex.set', { ...captured(), ...options, rules }) },
    macros: { register: registerMacro, substitute: substituteMacros,
      unregister: context.unregisterMacro },
    ui: { register: descriptor => {
      const { onClick, ...value } = descriptor;
      if (onClick) eventOn(`plugin:${pluginId}:button:${descriptor.id}`, onClick);
      return call('ui.register', { descriptor: value });
    }, unregister: id => { listeners.delete(`plugin:${pluginId}:button:${id}`); return call('ui.unregister', { id }); },
      open: id => call('ui.open', { id }), list: () => call('ui.list'),
      emit: (event, payload) => call('plugins.emitEvent', { event, payload }) },
    messages: { ...base.messages, read: options => call('messages.read', { ...captured(), ...options }),
      update: (messages, options) => call('messages.update', { ...captured(), messages, ...options }),
      insert: (messages, options) => call('messages.insert', { ...captured(), messages, ...options }),
      delete: (ids, options) => call('messages.delete', { ...captured(), ids, ...options }),
      swipes: (id, value, options) => call('messages.swipes', { ...captured(), ...options, ...value, id }),
      metadata: (id, value, options) => call('messages.metadata', { ...captured(), id, value, ...options }) },
    events: { ...base.events, on: (event, listener) => isNativeEvent(event) ? base.events.on(event, listener) : eventOn(event, listener).stop,
      off: (event, listener) => isNativeEvent(event) ? base.events.off(event, listener) : eventRemoveListener(event, listener),
      once: (event, listener) => {
        if (!isNativeEvent(event)) return eventOn(event, listener, 'last', true).stop;
        const stop = base.events.on(event, payload => { stop(); Promise.resolve().then(() => listener(payload)).catch(report); });
        return stop;
      }, emit: eventEmit },
  });
  for (const [name, value] of Object.entries(helpers)) Object.defineProperty(global, name, { value, configurable: true, enumerable: true, writable: true });
  Object.defineProperty(global, 'TavernHelper', { value: Object.freeze(helpers), configurable: true, writable: true });
  Object.defineProperty(global, 'SillyTavern', { value: Object.freeze({ getContext: () => { requireState(); return context; } }), configurable: true, writable: true });
  global.tavern_events = tavern_events; global.iframe_events = iframe_events;
  readyPromise.then(() => eventEmit(tavern_events.APP_READY)).catch(report);
  global.addEventListener('pagehide', () => { cleanups.forEach(stop => stop()); listeners.clear(); });
})(window);
