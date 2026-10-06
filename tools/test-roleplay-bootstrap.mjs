import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const kotlin = await readFile(new URL('../feature/conversation/src/main/java/com/eleckoi/android/feature/chat/ui/roleplay/web/document/runtime/RoleplayTranscriptBootstrap.kt', import.meta.url), 'utf8');
const source = kotlin.match(/= """([\s\S]*?)"""/)[1];
function fixture(messages = []) {
  const callbacks = [], posts = [];
  let atEnd = false, idle = true;
  const state = { messages, sessionId: 'new-chat', committedTransactionId: 1, fault: null,
    scroll: { geometryDirty: false }, initialPresentation: { phase: 'idle', epoch: 0, required: new Set() } };
  const context = vm.createContext({ state, turns: { querySelectorAll: () => [] },
    setTimeout: () => 1, clearTimeout() {}, window: { innerHeight: 800 },
    requestGeometryCommit: options => { if (options.afterCommit) callbacks.push(options.afterCommit); },
    authorApiIdle: () => idle, isAtPhysicalEnd: () => atEnd, richRootsWithin: () => [],
    refreshRichViewport() {}, notifyScrollState() {}, addEventListener() {}, post: value => posts.push(value),
    failRenderer: error => { throw error; } });
  vm.runInContext(source, context);
  vm.runInContext('beginInitialPresentation({transactionId:1,sessionId:"new-chat"});settleInitialPresentation({transactionId:1,sessionId:"new-chat"})', context);
  const drain = () => { for (let count = 0; callbacks.length && count < 20; count++) callbacks.shift()(); };
  return { state, posts, context, drain, setEnd: value => { atEnd = value; }, setIdle: value => { idle = value; } };
}
test('empty chat commits its first frame even when CSS leaves scrollable empty space', () => {
  const page = fixture(); page.drain();
  assert.equal(page.state.initialPresentation.phase, 'committed');
  assert.equal(page.posts[0].type, 'ready');
});
test('nonempty chat still waits for the actual message tail', () => {
  const page = fixture([{ id: 'message' }]); page.drain();
  assert.equal(page.state.initialPresentation.phase, 'settling');
  assert.equal(page.state.forceTail, true);
  page.setEnd(true); vm.runInContext('requestInitialPresentationCheck()', page.context); page.drain();
  assert.equal(page.state.initialPresentation.phase, 'committed');
});
test('empty chat still waits for pending author API operations to finish', () => {
  const page = fixture(); page.setIdle(false); page.drain();
  assert.equal(page.posts.length, 0);
  page.setIdle(true); vm.runInContext('requestInitialPresentationCheck()', page.context); page.drain();
  assert.equal(page.posts[0].type, 'ready');
});
