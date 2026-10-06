const db = await ElecKoi.storage.sqlite.open('memory.db');
await db.execute('CREATE TABLE IF NOT EXISTS memories(conversation TEXT NOT NULL, message TEXT NOT NULL, content TEXT NOT NULL, PRIMARY KEY(conversation,message))');

ElecKoi.events.on('message.committed', async ({ messageId, conversationId }) => {
  const rows = await db.query('SELECT message FROM memories WHERE conversation=? AND message=?', [conversationId, messageId]);
  if (rows.length) return;
  const messages = await ElecKoi.messages.read({ conversationId });
  const message = messages.find(value => value.id === messageId);
  if (!message) throw new Error('刚提交的回复不存在：' + messageId);
  const settings = await ElecKoi.settings.get();
  const extracted = await ElecKoi.generation.invoke({
    conversationId, configId: settings.configId || undefined, raw: true, purpose: 'memory-extraction', responseFormat: 'json',
    messages: [{ role: 'system', content: '从回复提取一条简短记忆，仅返回 JSON：{"memory":"人物、地点或事件"}。' },
      { role: 'user', content: message.content }],
  });
  const value = JSON.parse(extracted.content);
  if (typeof value.memory !== 'string') throw new Error('模型提取结果缺少 memory 字符串：' + extracted.content);
  await db.execute('INSERT OR REPLACE INTO memories(conversation,message,content) VALUES(?,?,?)', [conversationId, messageId, value.memory]);
});

ElecKoi.prompt.beforeGeneration(async ({ conversationId, purpose }) => {
  if (purpose !== 'chat' && purpose !== 'regenerate') return;
  const rows = await db.query('SELECT content FROM memories WHERE conversation=? ORDER BY rowid DESC LIMIT 12', [conversationId]);
  if (rows.length) {
    injectPrompts([{ id: 'test-memory', role: 'system', depth: 1,
      content: '已记录记忆：\n' + rows.map(value => value.content).reverse().join('\n') }], { once: true });
  }
});

const panel = `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
html,body{margin:0;min-height:100vh;font:15px system-ui;background:#f5f3ed;color:#252421}main{padding:16px}
textarea{box-sizing:border-box;width:100%;min-height:60px}table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #ccc}
button,select{padding:8px}#error{color:#b3261e;white-space:pre-wrap}
</style></head><body><main><h3>宿主接口测试</h3><p id="viewport"></p><label>提取模型 <select id="model"></select></label><button id="refresh">刷新</button>
<p id="error"></p><table><thead><tr><th>回复</th><th>记忆</th><th>操作</th></tr></thead><tbody id="rows"></tbody></table></main><script>
ElecKoi.ready().then(async()=>{
  const database=await ElecKoi.storage.sqlite.open('memory.db');
  const context=await ElecKoi.context.current();
  document.getElementById('viewport').textContent='100vh 实测：'+getComputedStyle(document.body).minHeight+'；innerHeight：'+innerHeight;
  const select=document.getElementById('model'),settings=await ElecKoi.settings.get(),models=await ElecKoi.chat.getModels();
  for(const config of models.items){const option=document.createElement('option');option.value=config.configId;option.textContent=config.name;select.append(option);}
  select.value=settings.configId||models.current.configId;
  select.onchange=async()=>{await ElecKoi.settings.set({...settings,configId:select.value});};
  const show=async()=>{
    const values=await database.query('SELECT * FROM memories WHERE conversation=? ORDER BY rowid',[context.conversationId]);
    const body=document.getElementById('rows');body.replaceChildren();
    for(const value of values){const row=document.createElement('tr'),id=document.createElement('td'),cell=document.createElement('td'),actions=document.createElement('td'),text=document.createElement('textarea'),save=document.createElement('button');
      id.textContent=value.message;text.value=value.content;save.textContent='保存';cell.append(text);actions.append(save);row.append(id,cell,actions);body.append(row);
      save.onclick=async()=>{try{await database.execute('UPDATE memories SET content=? WHERE conversation=? AND message=?',[text.value,context.conversationId,value.message]);}catch(error){document.getElementById('error').textContent=String(error);throw error;}};
    }
  };
  document.getElementById('refresh').onclick=()=>show().catch(error=>{document.getElementById('error').textContent=String(error);});
  await show();
}).catch(error=>{document.getElementById('error').textContent=String(error);console.error(error);});
</script></body></html>`;
await ElecKoi.ui.register({ id: 'memory-panel', label: '记忆数据库测试', kind: 'panel', html: panel });
await ElecKoi.ui.register({ id: 'memory-settings', label: '记忆提取模型', kind: 'settings', html: panel });
await ElecKoi.ui.register({ id: 'memory-message', label: '记忆', kind: 'message-button', html: panel });
