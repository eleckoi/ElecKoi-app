import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { projectionPlugin } from './request-projection.mjs'

const recordedRequestSnapshots = new Set()
const knownRequestContextDefinitions = new Map()

export function requestContextPath(root, runtimeThreadId) {
  return join(root, `${safeRuntimeThreadFile(runtimeThreadId)}.jsonl`)
}

/** Persist the exact, already-projected messages that will be sent by one real model request. */
export function recordRequestContextSnapshot(file, session, messages, plan = []) {
  if (typeof file !== 'string' || !file || !Array.isArray(messages)) return
  const boundary = session.snapshotEvents().findLast(event => event?.type === 'step/start')
  const turn = boundary?.data?.turn
  const step = boundary?.data?.step
  const requestSeq = boundary?.seq
  if (!Number.isSafeInteger(turn) || turn < 1 ||
    !Number.isSafeInteger(step) || step < 1 ||
    !Number.isSafeInteger(requestSeq) || requestSeq < 0) return

  const snapshotKey = `${file}\0${requestSeq}`
  if (recordedRequestSnapshots.has(snapshotKey)) return
  const items = requestContextItems(messages, plan)
  const definitions = requestContextDefinitionKeys(file)
  const pendingDefinitions = new Set()
  const rows = []
  const itemRefs = items.map(item => {
    const key = requestContextDefinitionKey(item)
    if (!definitions.has(key) && !pendingDefinitions.has(key)) {
      pendingDefinitions.add(key)
      const { order: _order, ...definition } = item
      rows.push({ type: 'definition', key, ...definition })
    }
    return { order: item.order, key }
  })
  rows.push({
    type: 'request',
    version: 1,
    requestSeq,
    turn,
    step,
    timeMillis: Number.isSafeInteger(boundary.time) && boundary.time >= 0 ? boundary.time : Date.now(),
    items: itemRefs,
  })

  try {
    mkdirSync(dirname(file), { recursive: true })
    appendFileSync(file, `${rows.map(row => JSON.stringify(row)).join('\n')}\n`, 'utf8')
    for (const key of pendingDefinitions) definitions.add(key)
    recordedRequestSnapshots.add(snapshotKey)
  } catch (error) {
    process.emitWarning(
      `ElecKoi could not persist request context: ${error instanceof Error ? error.message : String(error)}`,
    )
  }
}

export function requestContextItems(messages, plan = []) {
  const projectionByMessageId = new Map(plan.map(entry => [
    `${projectionPlugin}:${entry.id}`,
    entry,
  ]))
  const latestUserIndex = messages.findLastIndex(isDirectUserMessage)
  return messages.map((message, index) => {
    const projection = projectionByMessageId.get(String(message?.id ?? ''))
    const role = message?.role === 'system' || message?.role === 'assistant' ? message.role : 'user'
    if (projection) {
      return {
        order: index + 1,
        messageId: String(message?.id ?? ''),
        role,
        kind: 'prompt',
        title: projection.traceTitle || '设定提示词',
        source: projection.traceSource || positionLabel(projection.anchor),
        anchor: String(projection.anchor ?? ''),
        content: readableMessageContent(message),
      }
    }
    const source = message?.source && typeof message.source === 'object' ? message.source : {}
    const directUser = source.kind === 'user'
    return {
      order: index + 1,
      messageId: String(message?.id ?? ''),
      role,
      kind: requestContextKind(message, source),
      title: requestContextTitle(message, source, directUser && index === latestUserIndex),
      source: requestContextSource(source, directUser && index === latestUserIndex),
      anchor: '',
      content: readableMessageContent(message),
    }
  }).filter(item => item.content)
}

function requestContextDefinitionKeys(file) {
  const existing = knownRequestContextDefinitions.get(file)
  if (existing) return existing
  const keys = new Set()
  if (existsSync(file)) {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      if (!line.trim()) continue
      try {
        const value = JSON.parse(line)
        if (value?.type === 'definition' && typeof value.key === 'string') keys.add(value.key)
      } catch {
        continue
      }
    }
  }
  knownRequestContextDefinitions.set(file, keys)
  return keys
}

function requestContextDefinitionKey(item) {
  return createHash('sha256').update([
    item.messageId,
    item.role,
    item.kind,
    item.title,
    item.source,
    item.anchor,
    item.content,
  ].join('\0')).digest('hex')
}

function requestContextKind(message, source) {
  if (message?.role === 'system') return 'system'
  if (source.kind === 'tool' || message?.content?.some(block => block?.type === 'tool-result')) return 'tool'
  if (source.plugin === 'eleckoi-product-history') return 'history'
  if (source.kind === 'user') return 'user'
  if (message?.role === 'assistant') return 'assistant'
  return 'context'
}

function requestContextTitle(message, source, latestUser) {
  if (message?.role === 'system') return '系统提示词'
  if (source.kind === 'tool' || message?.content?.some(block => block?.type === 'tool-result')) return '工具结果'
  if (message?.content?.some(block => block?.type === 'tool-call')) return '助手工具调用'
  if (source.kind === 'user') return latestUser ? '用户最新输入' : '用户消息'
  if (source.plugin === 'eleckoi-product-history') {
    return message?.role === 'assistant' ? '历史助手消息' : '历史用户消息'
  }
  if (source.kind === 'model' || message?.role === 'assistant') return '助手消息'
  return source.sections?.[0]?.name || '上下文'
}

function requestContextSource(source, latestUser) {
  if (source.kind === 'user') return latestUser ? '本轮输入' : '聊天记录'
  if (source.kind === 'tool') return source.callId ? `工具结果 · ${source.callId}` : '工具结果'
  if (source.plugin === 'eleckoi-product-history') return '聊天记录'
  if (source.kind === 'model') return [source.provider, source.model].filter(Boolean).join(' · ') || '模型'
  if (source.kind === 'plugin') return source.plugin || '插件上下文'
  return source.kind || ''
}

function readableMessageContent(message) {
  return readableContent(message?.content)
}

function readableContent(content) {
  if (Array.isArray(content)) return content.map(readableBlock).filter(Boolean).join('\n\n')
  if (typeof content === 'string') return content
  if (content && typeof content === 'object') return readableBlock(content)
  return ''
}

function readableBlock(block) {
  if (!block || typeof block !== 'object') return ''
  if (block.type === 'text') return String(block.text ?? '')
  if (block.type === 'reasoning') return `思考\n${String(block.text ?? '')}`
  if (block.type === 'image') {
    const attachment = block.attachment && typeof block.attachment === 'object' ? block.attachment : {}
    return `[图片] ${attachment.name || attachment.id || attachment.mediaType || '图片附件'}`
  }
  if (block.type === 'file') {
    const attachment = block.attachment && typeof block.attachment === 'object' ? block.attachment : {}
    return `[文件] ${attachment.name || attachment.id || '文件附件'}`
  }
  if (block.type === 'tool-call') {
    return `调用工具 ${String(block.name || '')}\n${prettyJsonText(block.arguments)}`.trim()
  }
  if (block.type === 'tool-result') {
    const result = readableContent(block.content)
    return `${block.isError ? '工具返回错误' : '工具返回结果'}${result ? `\n${result}` : ''}`
  }
  try {
    return JSON.stringify(block, null, 2)
  } catch {
    return String(block.type || '')
  }
}

function prettyJsonText(value) {
  if (typeof value !== 'string') return String(value ?? '')
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function positionLabel(anchor) {
  return ({
    beforeToolContext: '设定插入点 1',
    toolContext: '缓存设定区',
    afterToolContext: '设定插入点 2',
    beforeHistory: '设定插入点 2',
    afterHistory: '设定插入点 3',
    beforeLatestUserInput: '设定插入点 3',
    afterLatestUserInput: '设定插入点 4',
    beforeToolFlow: '设定插入点 4',
    afterToolFlow: '设定插入点 5',
  })[anchor] || anchor || '设定位置'
}

function isDirectUserMessage(message) {
  return message?.role === 'user' && message?.source?.kind === 'user'
}

function safeRuntimeThreadFile(value) {
  return String(value ?? '').replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 160) || 'default'
}
